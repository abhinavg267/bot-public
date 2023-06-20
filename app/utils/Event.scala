package utils

import models.status_and_types.{OrderExecutionStatus, StrategyState}
import models.wrappers.StrategyStateVariables

sealed trait Event {
  def event: String
  def identifierName: String
  def identifier: Any
}

object Events {
  case class UpdateOrderSentStatus(orderId: Int, newOrderSentStatus: OrderExecutionStatus) extends Event {
    override def event: String = "UPDATE_ORDER_SENT_STATUS"
    override def identifierName: String = "orderId"
    override def identifier: Any = s"order Id $orderId, status: $newOrderSentStatus"
  }

  case class IncreaseOrderCount(strategyId: String) extends Event {
    override def event: String = "INCREASE_ORDER_COUNT"
    override def identifierName: String = "strategyId"
    override def identifier: Any = strategyId
  }

  case class StartStrategy(strategyId: String) extends Event {
    override def event: String = "START_STRATEGY"
    override def identifierName: String = "strategyId"
    override def identifier: Any = strategyId
  }

  case class UpdateStrategyState(strategyId: String, strategyState: StrategyState) extends Event {
    override def event: String = "UPDATE_STRATEGY_STATE"
    override def identifierName: String = "strategyId"
    override def identifier: Any = s"strategyId $strategyId, strategyState: $strategyState"
  }

  case class UpdateStrategyStateVariables(strategyId: String, strategyStateVariables: StrategyStateVariables) extends Event {
    override def event: String = "UPDATE_STRATEGY_STATE_VARIABLES"
    override def identifierName: String = "strategyId"
    override def identifier: Any = s"strategyId $strategyId, strategyStateVariables: $strategyStateVariables"
  }

  case class SendNotification(message: String) extends Event {
    override def event: String = "SEND_NOTIFICATION"
    override def identifierName: String = "message"
    override def identifier: Any = message
  }

  case class MarkOrderExecutedManually(orderId: Int) extends Event {
    override def event: String = "MARK_ORDER_EXECUTED_MANUALLY"
    override def identifierName: String = "orderId"
    override def identifier: Any = orderId
  }
}
