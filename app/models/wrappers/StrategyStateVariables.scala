package models.wrappers

import models.status_and_types.StrategyId
import models.status_and_types.StrategyId.ShortStraddle
import play.api.libs.json._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.MappedColumnType
import slick.jdbc.PostgresProfile.api._

sealed trait StrategyStateVariables {
  def strategyId: StrategyId
}

object StrategyStateVariables {
  case class ShortStraddleStateVariables(callOptionOpenState: TradingSymbolWithPrice,
                                         putOptionOpenState: TradingSymbolWithPrice,
                                         stopLoss: Option[TradingSymbolWithPrice])
    extends StrategyStateVariables {
    override def strategyId: StrategyId = StrategyId.ShortStraddle
  }

  implicit val jsonFormats: Format[StrategyStateVariables] = new Format[StrategyStateVariables] {
    override def writes(o: StrategyStateVariables): JsValue = (o match {
      case w: ShortStraddleStateVariables => Json.toJson(w)(Json.format[ShortStraddleStateVariables])
    }).asInstanceOf[JsObject] ++ Json.obj("strategyId" -> o.strategyId.asString)

    override def reads(json: JsValue): JsResult[StrategyStateVariables] = (json \ "strategyId").asOpt[StrategyId] match {
      case Some(strategyId) => strategyId match {
        case ShortStraddle =>
          throw new Exception(s"No StrategyStateVariable is defined for strategyId: $strategyId")
      }
      case None => throw new Exception(s"Cannot parse $json to StrategyStateVariable, since strategyId in not recognized")
    }
  }

  implicit val dbMapping: JdbcType[StrategyStateVariables] with BaseTypedType[StrategyStateVariables] =
    MappedColumnType.base[StrategyStateVariables, String](
      strategyStateVariables => Json.toJson(strategyStateVariables).toString(),
      strategyStateVariablesJsonStr => Json.fromJson[StrategyStateVariables](Json.parse(strategyStateVariablesJsonStr)) match {
        case JsSuccess(value, _) => value
        case JsError(errors) => throw new Exception(s"Cannot lift $strategyStateVariablesJsonStr as " +
          s"StrategyStateVariable from database due to json parsing errors: $errors")
      }
    )
}
