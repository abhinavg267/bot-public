package modules

import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Inject, Singleton}
import models.Tables
import org.joda.time.DateTime
import play.api.libs.concurrent.AkkaGuiceSupport
import services.broker_apis.BrokerAPI
import services.broker_apis.impl.{PaperTradeAPI, ZerodhaAPI}
import services.schedulers.TaskSchedulerActor
import services.strategies.Strategy
import services.strategies.impl.ShortStraddleStrategy

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class BindModule extends AbstractModule with AkkaGuiceSupport {
  override def configure(): Unit = {
    bind(classOf[ApplicationInit]).asEagerSingleton()

    bindActor[TaskSchedulerActor]("task-scheduler-actor")

    bind(classOf[Strategy]).annotatedWith(Names.named("short-straddle")).to(classOf[ShortStraddleStrategy])

    bind(classOf[BrokerAPI]).annotatedWith(Names.named("zerodha-api")).to(classOf[ZerodhaAPI])
    bind(classOf[BrokerAPI]).annotatedWith(Names.named("paper-trade-api")).to(classOf[PaperTradeAPI])
  }
}

@Singleton
class ApplicationInit @Inject()() {
  // initialize service constants
  ServiceConstants

  // ensure table availability tables
  Await.result(for {
    _ <- Tables.ensureTableAvailability()
  } yield (), Duration.Inf)

  // Read configs
  Strategy
}

object ServiceConstants {
  val serviceStartTime: DateTime = DateTime.now()
}
