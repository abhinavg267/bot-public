package services.data_providers

import com.google.inject.ImplementedBy
import services.broker_apis.impl.ZerodhaAPI

@ImplementedBy(classOf[ZerodhaAPI])
trait DataProvider {
  def getLTP(tradingSymbol: String): BigDecimal

  def getLTPOpt(tradingSymbol: String): Option[BigDecimal]
}
