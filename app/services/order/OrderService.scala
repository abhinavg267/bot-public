package services.order

import com.google.inject.ImplementedBy
import models._
import models.status_and_types._
import org.joda.time.DateTime
import services.order.impl.OrderServiceImpl
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

@ImplementedBy(classOf[OrderServiceImpl])
trait OrderService {
  def createAnOrderDBIO(tradingSymbol: String, transactionType: TransactionType, orderType: OrderType,
                        quantity: Int, productType: ProductType, triggerPrice: BigDecimal,
                        orderPrice: Option[BigDecimal], strategyId: String, strategyCounter: Int,
                        associatedOrder: Option[Int], triggerType: OrderTriggerType,
                        now: DateTime): DBIO[Int]

  def getExecutedOpenOrders(strategyId: String, strategyCounter: Int): DBIO[Seq[(Order, Int)]]

  def getAllOrders(strategyId: String, strategyCounter: Int): DBIO[Seq[Order]]

  def sendCreatedOrders(now: DateTime): Future[Unit]

  def markSentOrdersAsExecuted(now: DateTime): Future[Unit]

  def markOrderExecutedManually(orderId: Int, brokersOrderId: String, executionPrice: BigDecimal,
                                now: DateTime): Future[Order]

}
