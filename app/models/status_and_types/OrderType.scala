package models.status_and_types

import utils.{StringCompanion, WithAsString, WithDBMapping, WithJsonMapping}

sealed trait OrderType extends WithAsString
case object OrderType extends StringCompanion[OrderType]
  with WithDBMapping[OrderType] with WithJsonMapping[OrderType] {
  case object LIMIT extends OrderType {
    override def asString: String = "LIMIT"
  }

  // we will rarely use market order
  case object MARKET extends OrderType {
    override def asString: String = "MARKET"
  }

  override def all: Set[OrderType] = Set(LIMIT, MARKET)
}
