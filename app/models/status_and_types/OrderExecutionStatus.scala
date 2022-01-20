package models.status_and_types

import utils.{StringCompanion, WithAsString, WithDBMapping, WithJsonMapping}

sealed trait OrderExecutionStatus extends WithAsString
case object OrderExecutionStatus extends StringCompanion[OrderExecutionStatus]
  with WithDBMapping[OrderExecutionStatus] with WithJsonMapping[OrderExecutionStatus] {
  case object OrderCreated extends OrderExecutionStatus {
    override def asString: String = "ORDER_CREATED"
  }

  case object OrderSendInProgress extends OrderExecutionStatus {
    override def asString: String = "ORDER_SEND_IN_PROGRESS"
  }

  case object OrderSent extends OrderExecutionStatus {
    override def asString: String = "ORDER_SENT"
  }

  case object OrderSendFailed extends OrderExecutionStatus {
    override def asString: String = "ORDER_SEND_FAILED"
  }

  case object OrderExecuted extends OrderExecutionStatus {
    override def asString: String = "ORDER_EXECUTED"
  }

  case object OrderExecutedManually extends OrderExecutionStatus {
    override def asString: String = "ORDER_EXECUTED_MANUALLY"
  }

  override def all: Set[OrderExecutionStatus] = Set(OrderCreated, OrderSendInProgress, OrderSent, OrderSendFailed, OrderExecuted, OrderExecutedManually)
}

