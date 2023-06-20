package models.status_and_types

import utils.{StringCompanion, WithAsString, WithDBMapping}

sealed trait StrategyLifeCycleAction extends WithAsString

object StrategyLifeCycleAction extends StringCompanion[StrategyLifeCycleAction]
  with WithDBMapping[StrategyLifeCycleAction] {
  case object Start extends StrategyLifeCycleAction {
    override def asString: String = "START"
  }
  case object Execute extends StrategyLifeCycleAction {
    override def asString: String = "EXECUTE"
  }

  case object Stop extends StrategyLifeCycleAction {
    override def asString: String = "STOP"
  }

  override def all: Set[StrategyLifeCycleAction] = Set(Start, Execute, Stop)
}
