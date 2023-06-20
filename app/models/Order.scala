package models

import models.status_and_types.{OrderExecutionStatus, OrderTriggerType, OrderType, ProductType, TransactionType}
import org.joda.time.DateTime
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{TableQuery, Tag}
import utils.DBUtils.dateTimeDBMapping

case class Order(id: Int, tradingSymbol: String, transactionType: TransactionType, orderType: OrderType,
                 quantity: Int, productType: ProductType, triggerPrice: BigDecimal, orderPrice: Option[BigDecimal],
                 executionPrice: Option[BigDecimal], strategyId: String, strategyCounter: Int,
                 orderExecutionStatus: OrderExecutionStatus, brokersOrderId: Option[String],
                 triggerType: OrderTriggerType, createdAt: DateTime, updatedAt: DateTime) {
  val signedQuantity: Int = transactionType match {
    case TransactionType.Buy => quantity
    case TransactionType.Sell => -quantity
  }
}

class Orders(tag: Tag) extends Table[Order](tag, "orders") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def tradingSymbol = column[String]("trading_symbol")
  def transactionType = column[TransactionType]("transaction_type")
  def orderType = column[OrderType]("order_type")
  def quantity = column[Int]("quantity")
  def productType = column[ProductType]("product_type")
  def triggerPrice = column[BigDecimal]("trigger_price")
  def orderPrice = column[Option[BigDecimal]]("order_price")
  def executionPrice = column[Option[BigDecimal]]("execution_price")
  def strategyId = column[String]("strategy_id")
  def strategyCounter = column[Int]("strategy_counter")
  def orderExecutionStatus = column[OrderExecutionStatus]("order_execution_status")
  def brokersOrderId = column[Option[String]]("brokers_order_id")
  def triggerType = column[OrderTriggerType]("trigger_type")
  def createdAt = column[DateTime]("created_at")
  def updatedAt = column[DateTime]("updated_at")
  override def * = (id, tradingSymbol, transactionType, orderType, quantity, productType,
    triggerPrice, orderPrice, executionPrice, strategyId, strategyCounter, orderExecutionStatus, brokersOrderId, triggerType,
    createdAt, updatedAt) <> (
    (Order.apply _).tupled, Order.unapply
  )
}

object Orders {
  val query = TableQuery[Orders]
}
