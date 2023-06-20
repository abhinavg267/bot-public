package models

import org.joda.time.DateTime
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{TableQuery, Tag}
import utils.DBUtils.dateTimeDBMapping

/* Why is this required?
 * Ans - To know which orders are already squared - off.
 * 1. We cannot simply collect all the orders and find netQuantity,
 * since we won't be able to figure out the order price with this logic in case we want to put SL and TP for a particular order
 * */
case class OrderAssociation(buyOrderId: Int, sellOrderId: Int, quantity: Int, createdAt: DateTime)

class OrderAssociations(tag: Tag) extends Table[OrderAssociation](tag, "order_associations") {
  def buyOrderId = column[Int]("buy_order_id")
  def sellOrderId = column[Int]("sell_order_id")
  def quantity = column[Int]("quantity")
  def createdAt = column[DateTime]("created_at")
  override def * = (buyOrderId, sellOrderId, quantity, createdAt) <> (
    (OrderAssociation.apply _).tupled, OrderAssociation.unapply
  )
}

object OrderAssociations {
  val query = TableQuery[OrderAssociations]
}



