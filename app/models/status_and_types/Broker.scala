package models.status_and_types

import utils.{StringCompanion, WithAsString, WithDBMapping, WithJsonMapping}

sealed trait Broker extends WithAsString
case object Broker extends StringCompanion[Broker]
  with WithDBMapping[Broker] with WithJsonMapping[Broker] {
  case object Zerodha extends Broker {
    override def asString: String = "ZERODHA"
  }

  case object PaperTrade extends Broker {
    override def asString: String = "PAPER_TRADE"
  }

  override def all: Set[Broker] = Set(Zerodha, PaperTrade)
}
