package models.wrappers

import play.api.libs.json.{Json, OFormat}

case class TradingSymbolWithPrice(symbol: String, price: BigDecimal)

object TradingSymbolWithPrice {
  implicit val formats: OFormat[TradingSymbolWithPrice] = Json.format[TradingSymbolWithPrice]
}