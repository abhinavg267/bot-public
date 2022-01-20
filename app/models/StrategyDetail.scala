package models

import models.status_and_types.{Broker, StrategyState}
import models.wrappers.StrategyStateVariables
import org.joda.time.DateTime
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{TableQuery, Tag}
import utils.DBUtils.dateTimeDBMapping

case class StrategyDetail(strategyDetailId: Int, strategyId: String, counter: Int, broker: Broker,
                          orderCount: Int, maxOrderCount: Int, strategyState: StrategyState,
                          strategyStateVariables: Option[StrategyStateVariables], createdAt: DateTime,
                          updatedAt: DateTime, supersededBy: Option[Int]) {
  assert(orderCount <= maxOrderCount, s"Order count cannot be more than max Order count defined for " +
    s"strategy: $strategyId")
}

class StrategyDetails(tag: Tag) extends Table[StrategyDetail](tag, "strategy_details") {
  def id = column[Int]("id", O.AutoInc, O.PrimaryKey)
  def strategyId = column[String]("strategy_id")
  def counter = column[Int]("counter")
  def broker = column[Broker]("broker")
  def orderCount = column[Int]("order_count")
  def maxOrderCount = column[Int]("max_order_count")
  def strategyState = column[StrategyState]("strategy_state")
  def strategyStateVariables = column[Option[StrategyStateVariables]]("strategy_state_variables")
  def createdAt = column[DateTime]("created_at")
  def updatedAt = column[DateTime]("updated_at")
  def supersededBy = column[Option[Int]]("superseded_by")
  override def * = (id, strategyId, counter, broker, orderCount, maxOrderCount,
    strategyState, strategyStateVariables, createdAt, updatedAt, supersededBy) <> (
    (StrategyDetail.apply _).tupled, StrategyDetail.unapply)
}

object StrategyDetails {
  val query = TableQuery[StrategyDetails]
}




