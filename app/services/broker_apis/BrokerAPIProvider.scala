package services.broker_apis

import com.google.inject.name.Named
import com.google.inject.{ImplementedBy, Inject, Singleton}
import models.status_and_types.Broker

@ImplementedBy(classOf[BrokerAPIProviderImpl])
trait BrokerAPIProvider {
  def getBrokerAPI(broker: Broker): BrokerAPI
}

@Singleton
class BrokerAPIProviderImpl @Inject()(@Named("zerodha-api") zerodhaAPI: BrokerAPI,
                                      @Named("paper-trade-api") paperTradeAPI: BrokerAPI)
  extends BrokerAPIProvider {
  override def getBrokerAPI(broker: Broker): BrokerAPI = {
    broker match {
      case Broker.Zerodha => zerodhaAPI
      case Broker.PaperTrade => paperTradeAPI
    }
  }
}
