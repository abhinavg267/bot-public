package services.strategies

import models.Order
import models.status_and_types.{Broker, StrategyId}
import models.wrappers.TimeRange
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json._
import services.strategies.Strategy.StrategyParams.formats
import services.strategies.Strategy.{StrategyParams, getStrategyParams}
import utils.QualifiedConfiguration

import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import scala.concurrent.Future

trait Strategy {
  def id: StrategyId
  final def broker: Broker = strategyParams.broker
  final def maxOrderCountPerCounter: Int = strategyParams.maxOrderCountPerCounter
  final def isActive(now: DateTime): Boolean = strategyParams.activeDaysOfWeek.contains(DayOfWeek.of(now.getDayOfWeek))
  def logger: Logger
  def timeInstance: TimeRange
  def execute(now: DateTime): Future[Unit]

  val strategyParams: StrategyParams = getStrategyParams(id)
}

object Strategy {
  val dayOfWeekFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE")

  val mapFormats: Format[Map[StrategyId, StrategyParams]] = new Format[Map[StrategyId, StrategyParams]] {
    override def writes(o: Map[StrategyId, StrategyParams]): JsValue = o.foldRight(Json.obj()) {
      case ((strategyId, params), json) => json + (strategyId.asString -> Json.toJson(params))
    }
    override def reads(json: JsValue): JsResult[Map[StrategyId, StrategyParams]] =
      json.validate[Map[String, StrategyParams]].map {
        paramsByStringId => paramsByStringId.map { case (str, params) =>
          StrategyId.fromString(str) -> params
        }
      }
  }

  implicit val daysOfWeekFormats: Format[Set[DayOfWeek]] = new Format[Set[DayOfWeek]] {
    override def writes(o: Set[DayOfWeek]): JsValue = Json.toJson(o.map(dayOfWeekFormat.format(_)).toSeq)

    override def reads(json: JsValue): JsResult[Set[DayOfWeek]] = json.validate[Seq[String]] match {
      case JsSuccess(dayOfWeekStrSeq, _) => JsSuccess(dayOfWeekStrSeq.map { dayOfWeekStr =>
        DayOfWeek.from(dayOfWeekFormat.parse(dayOfWeekStr))
      }.toSet)
      case JsError(errors) => throw new Exception(s"Cannot parse $json as set of day of week due to $errors")
    }
  }

  case class StrategyParams(broker: Broker, maxOrderCountPerCounter: Int,
                            activeDaysOfWeek: Set[DayOfWeek])

  object StrategyParams {
    implicit val formats: OFormat[StrategyParams] = Json.format[StrategyParams]
  }

  private val strategyParamsMap: Map[StrategyId, StrategyParams] = Json.fromJson[Map[StrategyId, StrategyParams]](
    QualifiedConfiguration.getJson("strategyParams"))(mapFormats) match {
      case JsSuccess(value, _) => value
      case JsError(errors) => throw new Exception(s"Cannot parse strategy params due to $errors")
    }

  StrategyId.all.foreach { id =>
    assert(strategyParamsMap.keys.toSet.contains(id), s"Cannot find strategy params for $id, while parsing the conf")
  }

  strategyParamsMap.foreach { case (id, params) =>
    println(s"Active days for $id: ${params.activeDaysOfWeek}")
  }

  def getStrategyParams(strategyId: StrategyId): StrategyParams = strategyParamsMap.getOrElse(strategyId,
    throw new Exception(s"Cannot find strategy params for $strategyId"))

  // CUSTOM EXCEPTIONS
  case class OpenOrderWithInvalidTransactionTypeException(id: StrategyId, openOrderWithInvalidTransactionType: Order)
    extends Exception(s"Strategy $id cannot handle open order of type " +
      s"${openOrderWithInvalidTransactionType.transactionType}. Open order: $openOrderWithInvalidTransactionType")
}
