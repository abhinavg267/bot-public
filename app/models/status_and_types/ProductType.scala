package models.status_and_types

import utils.{StringCompanion, WithAsString, WithDBMapping, WithJsonMapping}

sealed trait ProductType extends WithAsString
case object ProductType extends StringCompanion[ProductType]
  with WithDBMapping[ProductType] with WithJsonMapping[ProductType] {
  // for equity
  case object CNC extends ProductType {
    override def asString: String = "CNC"
  }

  // for future and options
  case object NRML extends ProductType {
    override def asString: String = "NRML"
  }

  // for future and options
  case object MIS extends ProductType {
    override def asString: String = "MIS"
  }

  override def all: Set[ProductType] = Set(NRML, MIS, CNC)
}

