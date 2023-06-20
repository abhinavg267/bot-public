package services.broker_apis.impl

import com.google.inject.{Inject, Singleton}
import models.status_and_types.Broker
import models.status_and_types.Broker.PaperTrade
import services.broker_apis.BrokerAPI
import services.broker_apis.BrokerAPI.IsOrderCompleteResponse
import services.data_providers.DataProvider
import services.notifications.MessagingService
import utils.LoggerWithNotification

import java.net.SocketException
import scala.util.Random

@Singleton
class PaperTradeAPI @Inject()(dataProvider: DataProvider, messagingService: MessagingService) extends BrokerAPI {
  override def broker: Broker = PaperTrade

  override val logger: LoggerWithNotification = new LoggerWithNotification(s"broker.$broker", messagingService)

  override def getLoginUrl: String = "http://127.0.0.1"

  override def createSession(requestToken: String): Unit =
    logger.info(notify = true)("Session created successfully!")

  def placeOrder(order: models.Order): String = {
//    val sleepTime = (Math.abs(Random.nextInt()).asInstanceOf[Long]*1000) % 3000
//    val sleepTime = 2000
//    logger.info(notify = false)(s"Place order api will respond back in ${sleepTime / 1000} seconds")
//    Thread.sleep(sleepTime)

    if((Random.nextInt() % 8) == 0) throw new SocketException(s"Network failure for order: $order")
    logger.info(notify = true)(s"Order received: $order")
    s"PAPER_TRADE_${order.id}"
  }

  def isOrderComplete(brokersOrderId: String, tradingSymbol: String): IsOrderCompleteResponse = {
    if((Random.nextInt() % 50) == 0) throw new SocketException(s"Network failure for brokerOrderId: $brokersOrderId")
    val isOrderComplete: Boolean = (Random.nextInt() % 2) == 0
    IsOrderCompleteResponse(isOrderComplete, Some(dataProvider.getLTP(tradingSymbol)))
  }

  override def validateTotalCompletedOrders(): Unit = ()
}
