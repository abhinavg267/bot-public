package services.broker_apis.impl

import com.google.inject.{Inject, Singleton}
import com.zerodhatech.kiteconnect.KiteConnect
import com.zerodhatech.kiteconnect.kitehttp.exceptions.NetworkException
import com.zerodhatech.kiteconnect.utils.{Constants => ZerodhaConstants}
import com.zerodhatech.models._
import models.status_and_types.{Broker, OrderType}
import models.status_and_types.Broker.Zerodha
import services.broker_apis.BrokerAPI
import services.broker_apis.BrokerAPI.IsOrderCompleteResponse
import services.data_providers.DataProvider
import services.notifications.MessagingService
import utils.{JavaConversionUtils, LoggerWithNotification, QualifiedConfiguration}

import scala.collection.JavaConverters
import scala.util.Random

@Singleton
class ZerodhaAPI @Inject()(messagingService: MessagingService) extends BrokerAPI with DataProvider {
  private val apiKey: String = QualifiedConfiguration.getString("kite.api_key")
  private val apiSecret: String = QualifiedConfiguration.getString("kite.api_secret")
  private val maxCompletedOrdersPerDay: Int = QualifiedConfiguration.getString("kite.max_completed_orders_per_day").toInt
  private val maxOrdersPerDay: Int = QualifiedConfiguration.getString("kite.max_orders_per_day").toInt
  private val kiteConnect = new KiteConnect(apiKey)

  override val logger: LoggerWithNotification = new LoggerWithNotification(s"broker.$broker", messagingService)

  override def broker: Broker = Zerodha

  override def getLoginUrl: String = {
    kiteConnect.getLoginURL
  }

  override def createSession(requestToken: String): Unit = {
    val user: User =  kiteConnect.generateSession(requestToken, apiSecret)
    kiteConnect.setAccessToken(user.accessToken)
    kiteConnect.setPublicToken(user.publicToken)
  }

  override def getLTP(tradingSymbol: String): BigDecimal = getLTPOpt(tradingSymbol).getOrElse(
    throw new Exception(s"Could not find LTP for $tradingSymbol"))

  override def getLTPOpt(tradingSymbol: String): Option[BigDecimal] = {
//    if((Random.nextInt() % 5) == 0) throw new NetworkException("network failure", 100)

    val ltpQuote = kiteConnect.getLTP(Array(tradingSymbol)).get(tradingSymbol)
    if(ltpQuote != null) Some(ltpQuote.lastPrice)
    else {
      logger.warn(notify = true)(s"Could not find LTP for $tradingSymbol")
      None
    }
  }

  override def placeOrder(order: models.Order): String = {
    val exchange :: tradingSymbol :: Nil = order.tradingSymbol.split(":").toList
    val strategyId = order.strategyId.replace("_", "")

    val orderParams = new OrderParams
    orderParams.tradingsymbol = tradingSymbol
    orderParams.exchange = exchange
    orderParams.transactionType = order.transactionType.asString
    orderParams.orderType = order.orderType.asString
    orderParams.quantity = order.quantity
    orderParams.product = order.productType.asString
    orderParams.price = order.orderType match {
      case OrderType.LIMIT => order.orderPrice.getOrElse(throw new Exception(s"Cannot get order price for order of " +
        s"type LIMIT. Order $order")).toDouble
      case OrderType.MARKET => null
    }
    orderParams.validity = ZerodhaConstants.VALIDITY_DAY
    orderParams.tag = strategyId.substring(0, Math.min(strategyId.length, 8));

    val kiteOrder = kiteConnect.placeOrder(orderParams, ZerodhaConstants.VARIETY_REGULAR)
    logger.info(notify = true)(s"Order received: $order")
    kiteOrder.orderId
  }

  override def isOrderComplete(brokersOrderId: String, tradingSymbol: String): IsOrderCompleteResponse = {
    val orderHistoryJava = kiteConnect.getOrderHistory(brokersOrderId)
    val orderHistory: Seq[Order] = JavaConverters.asScalaIteratorConverter(orderHistoryJava.iterator).asScala.toSeq
    val completedOrderOpt: Option[Order] = orderHistory.find(_.status == ZerodhaConstants.ORDER_COMPLETE)
    completedOrderOpt match {
      case Some(completedOrder) =>
        IsOrderCompleteResponse(isOrderComplete = true, Some(completedOrder.averagePrice.toDouble))

      case None => IsOrderCompleteResponse(isOrderComplete = false, None)
    }
  }

  override def validateTotalCompletedOrders(): Unit = {
    val orders = JavaConversionUtils.toList(kiteConnect.getOrders)
    val completedOrders = orders.filter(_.status == ZerodhaConstants.ORDER_COMPLETE)
    if(completedOrders.length > maxCompletedOrdersPerDay) {
      logger.error(notify = true)(s"Found more completed orders then allowed in Zerodha. " +
        s"Allowed completed orders: $maxCompletedOrdersPerDay, Found completed orders: ${completedOrders.length}")
      System.exit(0)
    } else if(orders.length > maxOrdersPerDay) {
      logger.error(notify = true)(s"Found more orders then allowed in Zerodha. " +
        s"Allowed orders: $maxOrdersPerDay,  Found orders: ${completedOrders.length}")
      System.exit(0)
    } else {
      logger.info(notify = false)(s"Found ${completedOrders.length} completed orders and ${orders.length} " +
        s"total orders in Zerodha.")
    }
  }
}
