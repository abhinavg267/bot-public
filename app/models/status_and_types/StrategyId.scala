package models.status_and_types

import utils.{StringCompanion, WithAsString, WithDBMapping, WithJsonMapping}

sealed trait StrategyId extends WithAsString
case object StrategyId extends StringCompanion[StrategyId]
  with WithDBMapping[StrategyId] with WithJsonMapping[StrategyId] {
  case object ShortStraddle extends StrategyId {
    override def asString: String = "SHORT_STRADDLE"
  }

  override def all: Set[StrategyId] = Set(ShortStraddle)
}

