package models.status_and_types

import utils.{StringCompanion, WithAsString, WithDBMapping, WithJsonMapping}

sealed trait TransactionType extends WithAsString
case object TransactionType extends StringCompanion[TransactionType]
  with WithDBMapping[TransactionType] with WithJsonMapping[TransactionType] {
  case object Buy extends TransactionType {
    override def asString: String = "BUY"
  }

  case object Sell extends TransactionType {
    override def asString: String = "SELL"
  }

  override def all: Set[TransactionType] = Set(Buy, Sell)
}
