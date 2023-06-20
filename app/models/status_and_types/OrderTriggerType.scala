package models.status_and_types

import utils.{StringCompanion, WithAsString, WithDBMapping, WithJsonMapping}

sealed trait OrderTriggerType extends WithAsString
case object OrderTriggerType extends StringCompanion[OrderTriggerType]
  with WithDBMapping[OrderTriggerType] with WithJsonMapping[OrderTriggerType] {
  case object Entry extends OrderTriggerType {
    override def asString: String = "ENTRY"
  }

  case object StopLoss extends OrderTriggerType {
    override def asString: String = "STOP_LOSS"
  }

  case object TakeProfit extends OrderTriggerType {
    override def asString: String = "TAKE_PROFIT"
  }

  case object Exit extends OrderTriggerType {
    override def asString: String = "EXIT"
  }

  override def all: Set[OrderTriggerType] = Set(Entry, StopLoss, TakeProfit, Exit)
}
