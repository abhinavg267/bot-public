package services.broker_apis

import models.Order
import models.status_and_types.Broker
import services.broker_apis.BrokerAPI.IsOrderCompleteResponse
import utils.LoggerWithNotification

trait BrokerAPI {
  def broker: Broker

  def logger: LoggerWithNotification

  def getLoginUrl: String

  def createSession(requestToken: String): Unit

  def placeOrder(order: Order): String

  def isOrderComplete(brokersOrderId: String, tradingSymbol: String): IsOrderCompleteResponse

  def validateTotalCompletedOrders(): Unit
}

object BrokerAPI {
  case class IsOrderCompleteResponse(isOrderComplete: Boolean, orderExecutionPrice: Option[BigDecimal])

  case class BrokerAPIException(broker: Broker, message: String)
    extends Exception(s"$broker API failed with message $message")
}
