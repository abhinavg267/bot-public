package services.strategies.impl

import com.google.inject.{Inject, Singleton}
import models.StrategyDetail
import models.status_and_types.OrderTriggerType.{StopLoss, TakeProfit}
import models.status_and_types.TransactionType.Buy
import models.status_and_types._
import models.wrappers.TimeRange
import modules.ServiceConstants
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import play.api.Logger
import services.data_providers.DataProvider
import services.order.OrderService
import services.strategies.Strategy
import services.strategies.Strategy.OpenOrderWithInvalidTransactionTypeException
import services.strategies.details.StrategyDetailsService
import slick.dbio.DBIO
import utils.DBUtils.{dbRunNonTransactionally, dbRunTransactionally}
import utils.TradingSymbolUtilities._
import utils.{DateTimeUtils, FutureUtils, QualifiedConfiguration}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ShortStraddleStrategy @Inject()(orderService: OrderService,
                                      dataProvider: DataProvider,
                                      strategyDetailsService: StrategyDetailsService) extends Strategy {
  private val orderQuantity: Int = 50
  private val allowedSlippageForLimitOrders: BigDecimal = 0.2 // 20%
  private val stopLossPoints = 40
  private val takeProfitPoints = 70
  private def startDateTime(now: DateTime) = DateTimeUtils.convertTimeToDateTime(timeInstance.startTime, now)
  private def endDateTime(now: DateTime) = DateTimeUtils.convertTimeToDateTime(timeInstance.endTime, now)

  override val logger: Logger = Logger(s"strategy.$id")

  override def id: StrategyId = StrategyId.ShortStraddle

  private def startStrategy(now: DateTime): DBIO[Unit] = {
    logger.info(s"$id startup triggered...")

    val niftyLTP: BigDecimal = dataProvider.getLTP(niftyTradingSymbol)
    val niftyATMPrice = getNiftyATMPrice(niftyLTP)
    val niftyWeeklyExpiry = getWeeklyDateOfExpiry(now)
    def niftyOptionSymbol(deltaFromATM: Int, optionType: String): String =
      getOptionTradingSymbol(niftyOptionPrefix, niftyWeeklyExpiry, niftyATMPrice + deltaFromATM, optionType)

    val niftyATMCallOptionSymbol = niftyOptionSymbol(0, "CE")
    val niftyATMCallLTP = dataProvider.getLTP(niftyATMCallOptionSymbol)

    val niftyATMPutOptionSymbol = niftyOptionSymbol(0, "PE")
    val niftyATMPutLTP = dataProvider.getLTP(niftyATMPutOptionSymbol)

    for {
      strategyDetail <- strategyDetailsService.addStrategyDetailsOnStrategyStartup(id.asString, broker,
        maxOrderCountPerCounter = maxOrderCountPerCounter, strategyStateVariables = None, now)

      _ <- orderService.createAnOrderDBIO(niftyATMPutOptionSymbol, TransactionType.Sell, OrderType.LIMIT,
        orderQuantity, ProductType.MIS, triggerPrice = niftyATMPutLTP, orderPrice = Some(niftyATMPutLTP*(1-allowedSlippageForLimitOrders)),
        strategyDetail.strategyId, strategyDetail.counter, associatedOrder = None, OrderTriggerType.Entry, now)

      _ <- orderService.createAnOrderDBIO(niftyATMCallOptionSymbol, TransactionType.Sell, OrderType.LIMIT,
        orderQuantity, ProductType.MIS, triggerPrice = niftyATMCallLTP, orderPrice = Some(niftyATMCallLTP*(1-allowedSlippageForLimitOrders)),
        strategyDetail.strategyId, strategyDetail.counter, associatedOrder = None, OrderTriggerType.Entry, now)

    } yield logger.info(s"$id started successfully!!")
  }

  override def execute(now: DateTime): Future[Unit] = {
    logger.info(s"$id running...")

    for {
      strategyDetailOpt <- dbRunNonTransactionally(strategyDetailsService.getLatestStrategyDetailByStrategyIdOpt(id.asString))

      // OPEN
      _ <- if(strategyDetailOpt.fold(true)(_.strategyState == StrategyState.Closed) && now.isAfter(startDateTime(now)) && now.isBefore(endDateTime(now)) && isActive(now))
        dbRunTransactionally(startStrategy(now)) else Future.successful(Unit)

      _ <- strategyDetailOpt match {
        case Some(strategyDetail) => for {
          // CLOSE
          _ <- if(strategyDetail.strategyState == StrategyState.Running && now.isAfter(endDateTime(now)))
            endStrategy(strategyDetail, now) else Future.successful(Unit)

          // EXECUTE
          _ <-
            if(strategyDetail.strategyState == StrategyState.Running && now.isAfter(startDateTime(now)) && now.isBefore(endDateTime(now))) {
              logger.info(s"$id execution triggered...")
              for {
                allOrders <- dbRunTransactionally(orderService.getAllOrders(strategyDetail.strategyId, strategyDetail.counter))
                executedOpenOrdersAndQuantity <- dbRunTransactionally(orderService.getExecutedOpenOrders(strategyDetail.strategyId, strategyDetail.counter))
                isStopLossTriggeredForAnyOrder = allOrders.exists(_.triggerType == OrderTriggerType.StopLoss)

                _ <- FutureUtils.runSequentially(executedOpenOrdersAndQuantity, Future.successful(Seq.empty[Unit])) { case (executedOpenOrder, openQuantity) =>
                  val ltp = dataProvider.getLTP(executedOpenOrder.tradingSymbol)
                  val executionPrice = executedOpenOrder.executionPrice.getOrElse(throw new Exception(s"Cannot find execution " +
                    s"price for executedOpenOrder: $executedOpenOrder"))
                  dbRunTransactionally {
                    executedOpenOrder.transactionType match {
                      case TransactionType.Buy => throw OpenOrderWithInvalidTransactionTypeException(id, executedOpenOrder)

                      case TransactionType.Sell =>
                        val triggerTypeAndOrderPriceOpt = {
                          if(ltp < executionPrice - takeProfitPoints) Some((TakeProfit, executionPrice - takeProfitPoints))
                          else if(ltp > executionPrice + stopLossPoints) Some((StopLoss, executionPrice + stopLossPoints))
                          else if(isStopLossTriggeredForAnyOrder && ltp > executionPrice) Some((StopLoss, executionPrice))
                          else None
                        }

                        triggerTypeAndOrderPriceOpt match {
                          case Some((triggerType, orderPrice)) =>
                            orderService.createAnOrderDBIO(executedOpenOrder.tradingSymbol, TransactionType.Buy, OrderType.LIMIT,
                              openQuantity, executedOpenOrder.productType, triggerPrice = ltp ,orderPrice = Some(orderPrice*(1+allowedSlippageForLimitOrders)),
                              strategyDetail.strategyId, strategyDetail.counter, associatedOrder = Some(executedOpenOrder.id), triggerType, now).map(_ => ())

                          case None => DBIO.successful(Unit)
                        }
                    }
                  }
                }
              } yield logger.info(s"$id executed successfully!!")
            }

            // condition for execution is not satisfied
            else Future.successful(Unit)

        } yield ()
        case None => Future.successful(Unit)
      }
    } yield ()
  }

  private def endStrategy(strategyDetail: StrategyDetail, now: DateTime): Future[Unit] = {
    logger.info(s"$id closing triggered...")

    for {
      executedOpenOrdersAndQuantity <- dbRunTransactionally(orderService.getExecutedOpenOrders(strategyDetail.strategyId, strategyDetail.counter))
      _ <- FutureUtils.runSequentially(executedOpenOrdersAndQuantity, Future.successful(Seq.empty[Unit])) { case (executedOpenOrder, openQuantity) =>
        val ltp = dataProvider.getLTP(executedOpenOrder.tradingSymbol)
        val executionPrice = executedOpenOrder.executionPrice.getOrElse(throw new Exception(s"Cannot find execution " +
          s"price for executedOpenOrder: $executedOpenOrder"))

        dbRunTransactionally(executedOpenOrder.transactionType match {
          case TransactionType.Buy => throw OpenOrderWithInvalidTransactionTypeException(id, executedOpenOrder)

          case TransactionType.Sell =>
            orderService.createAnOrderDBIO(executedOpenOrder.tradingSymbol, TransactionType.Buy, OrderType.LIMIT,
              openQuantity, executedOpenOrder.productType, triggerPrice = ltp, orderPrice = Some((executionPrice + stopLossPoints)*(1+allowedSlippageForLimitOrders)),
              strategyDetail.strategyId, strategyDetail.counter, associatedOrder = Some(executedOpenOrder.id), OrderTriggerType.Exit, now).map(_ => ())
        })
      }
      _ <- dbRunTransactionally(strategyDetailsService.updateStrategyState(id, StrategyState.Closed, now))
    } yield logger.info(s"$id closed successfully!!")
  }

  ////// PRIVATE
  def timeString(date: DateTime): String = {
    s"${date.hourOfDay().get()}:${date.minuteOfHour().get()}"
  }
  private val timeFormat: DateTimeFormatter = DateTimeFormat.forPattern("HH:mm")

  override def timeInstance: TimeRange = {
    QualifiedConfiguration.playEnv match {
      case PlayEnv.Dev | PlayEnv.ProdEnvTesting =>
        val initialDelay = 120
        val delay = 480
        val start1Time: DateTime = DateTime.parse(timeString(ServiceConstants.serviceStartTime.plusSeconds(initialDelay)), timeFormat)
        val end1Time: DateTime = DateTime.parse(timeString(ServiceConstants.serviceStartTime.plusSeconds(initialDelay+delay)), timeFormat)

        TimeRange(start1Time, end1Time)
      case PlayEnv.Stage | PlayEnv.Prod =>
        val start1Time: DateTime = DateTime.parse("09:30", timeFormat)
        val end1Time: DateTime = DateTime.parse("12:15", timeFormat)
        TimeRange(start1Time, end1Time)
    }
  }
}

