package models.status_and_types

import utils.{StringCompanion, WithAsString, WithDBMapping, WithJsonMapping}

sealed trait StrategyState extends WithAsString
case object StrategyState extends StringCompanion[StrategyState]
  with WithDBMapping[StrategyState] with WithJsonMapping[StrategyState] {
  case object Running extends StrategyState {
    override def asString: String = "RUNNING"
  }

  case object Closed extends StrategyState {
    override def asString: String = "CLOSED"
  }

  override def all: Set[StrategyState] = Set(Running, Closed)
}
