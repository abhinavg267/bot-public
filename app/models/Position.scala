package models

import org.joda.time.DateTime
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{TableQuery, Tag}
import utils.DBUtils.dateTimeDBMapping

case class Position(id: Int, tradingSymbol: String, openQuantity: Int, strategyId: String,
                    strategyCounter: Int, createdAt: DateTime, updatedAt: DateTime)

class Positions(tag: Tag) extends Table[Position](tag, "positions") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def tradingSymbol = column[String]("trading_symbol")
  def openQuantity = column[Int]("open_quantity")
  def strategyId = column[String]("strategy_id")
  def strategyCounter = column[Int]("strategy_counter")
  def createdAt = column[DateTime]("created_at")
  def updatedAt = column[DateTime]("updated_at")
  override def * = (id, tradingSymbol, openQuantity, strategyId, strategyCounter,
    createdAt, updatedAt) <> (
    (Position.apply _).tupled, Position.unapply
  )
}

object Positions {
  val query = TableQuery[Positions]
}

