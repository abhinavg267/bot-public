package services.schedulers

import akka.actor.{Actor, ActorRef, ActorSystem}
import com.google.inject.name.Named
import com.google.inject.{ImplementedBy, Inject, Singleton}
import com.zerodhatech.kiteconnect.kitehttp.exceptions.{KiteException, NetworkException}
import models.status_and_types.{Broker, StrategyId}
import org.joda.time.DateTime
import services.broker_apis.{BrokerAPI, BrokerAPIProvider}
import services.notifications.MessagingService
import services.order.OrderService
import services.strategies.Strategy
import utils.CustomExceptions.{FailedToFetchOrderCompletionStatus, OrderCompletionDelay, OrderPlacementFailure}
import utils.FutureUtils.{SafeFutures, runSequentially}
import utils.LoggerWithNotification
import utils.LoggingUtils.logger

import java.net.{SocketException, SocketTimeoutException}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Random, Try}

@ImplementedBy(classOf[TaskSchedulerImpl])
sealed trait TaskScheduler {
  def triggerAllSchedules(): Unit
}

@Singleton
class TaskSchedulerImpl @Inject() (system: ActorSystem,
                                   @Named("task-scheduler-actor") schedulerActor: ActorRef) extends TaskScheduler {
  override def triggerAllSchedules(): Unit = {
    system.scheduler.scheduleWithFixedDelay(0.milliseconds, 100.milliseconds, schedulerActor,
      "sendCreatedOrders")

    system.scheduler.scheduleWithFixedDelay(0.milliseconds, 2.seconds, schedulerActor,
      "markSentOrdersAsExecuted")

    system.scheduler.scheduleWithFixedDelay(0.milliseconds, 2.seconds, schedulerActor,
      "validateTotalCompletedOrders")

    system.scheduler.scheduleWithFixedDelay(0.milliseconds, 1.seconds, schedulerActor,
      "executeStrategies")
  }
}

/** Throwing error from every handler so that it will stop the scheduler
 * The throwable returned from the method must be explicitly handled to avoid stopping scheduler.
 * */
class TaskSchedulerActor @Inject() (orderService: OrderService,
                                    messagingService: MessagingService,
                                    brokerAPIProvider: BrokerAPIProvider,
                                    @Named("short-straddle") shortStraddleStrategy: Strategy) extends Actor {
  val loggerWithNotification = new LoggerWithNotification("scheduler", messagingService)
  val zerodhaAPI: BrokerAPI = brokerAPIProvider.getBrokerAPI(Broker.Zerodha)

  private val allStrategies: Set[Strategy] = StrategyId.all.map {
    case StrategyId.ShortStraddle => shortStraddleStrategy
  }

  def receive: Receive = {
    case "sendCreatedOrders" => sendCreatedOrders()
    case "markSentOrdersAsExecuted" => markSentOrdersAsExecuted()
    case "validateTotalCompletedOrders" => validateTotalCompletedOrders()
    case "executeStrategies" => executeStrategies()
  }

  def executeStrategies(): Seq[Unit] = {
    val now = DateTime.now()
    tryItSafely("executeStrategies")(
      runSequentially(allStrategies.toSeq, Future.successful(Seq.empty[Unit])) { strategy =>
        strategy.execute(now)
      }
    )
  }

  def sendCreatedOrders(): Unit = {
    val now = DateTime.now()
    if(Random.nextInt() % 50 == 0) logger.info(s"Checking if created orders are sent...")
    tryItSafely("sendCreatedOrders")(orderService.sendCreatedOrders(now))
  }

  def markSentOrdersAsExecuted(): Unit = {
    val now = DateTime.now()
    logger.info(s"Checking if sent orders are completed...")
    tryItSafely("markSentOrdersAsExecuted")(orderService.markSentOrdersAsExecuted(now))
  }

  def validateTotalCompletedOrders(): Unit = {
    logger.info(s"Validating total completed orders...")
    tryItSuperSafely("validateTotalCompletedOrders")(Future(zerodhaAPI.validateTotalCompletedOrders()))
  }

  def tryItSafely[T](api: String)(f: => Future[T]): T = {
    val response = Try {
      f
    }.safely.map {
      case Left(ex: SocketTimeoutException) => throw new Exception(ex)
      case Left(ex: SocketException) => throw new Exception(ex)
      case Left(ex: NetworkException) => throw new Exception(ex.message, ex)
      case Left(opf: OrderPlacementFailure) =>
        loggerWithNotification.error(notify = true)(s"Error while $api: ${opf.message}. " +
          s"Handle the order placement manually and update brokers order id and execution price.")
        throw opf
      case Left(ocd: OrderCompletionDelay) =>
        loggerWithNotification.error(notify = true)(s"Error while $api: ${ocd.getMessage}. " +
          s"Check the status. Place new order if required and update order id and execution price iff new order is placed.")
        throw ocd
      case Left(ocs: FailedToFetchOrderCompletionStatus) =>
        loggerWithNotification.error(notify = true)(s"Error while $api: ${ocs.getMessage}.")
        throw ocs
      case Left(kiteException: KiteException) =>
        loggerWithNotification.error(notify = true)(s"Error while $api: ${kiteException.message}. Error Code: ${kiteException.code}. Shutting down scheduler.")
        throw new Throwable(kiteException.message, kiteException)
      case Left(throwable) =>
        loggerWithNotification.error(notify = true)(s"Error while $api: ${throwable.getMessage}. Shutting down scheduler.")
        throw new Throwable(throwable.getMessage, throwable)
      case Right(value) => value
    }

    Await.result(response, Duration.Inf)
  }

  def tryItSuperSafely[T](api: String)(f: => Future[T]): T = {
    val response = Try {
      f
    }.safely.map {
      case Left(throwable) =>
        loggerWithNotification.error(notify = true)(s"Error while $api: ${throwable.getMessage}. Everything " +
          s"will be running continuously, the method is executing super safely and cannot crash the scheduler.")
        throw new Exception(throwable.getMessage, throwable)
      case Right(value) => value
    }

    Await.result(response, Duration.Inf)
  }
}
