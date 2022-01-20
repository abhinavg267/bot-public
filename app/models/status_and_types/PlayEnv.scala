package models.status_and_types

import utils.{StringCompanion, WithAsString, WithDBMapping}

sealed trait PlayEnv extends WithAsString
case object PlayEnv extends StringCompanion[PlayEnv]
  with WithDBMapping[PlayEnv] {
  case object Dev extends PlayEnv {
    override def asString: String = "dev"
  }

  case object Stage extends PlayEnv {
    override def asString: String = "stage"
  }

  case object ProdEnvTesting extends PlayEnv {
    override def asString: String = "prod_env_testing"
  }

  case object Prod extends PlayEnv {
    override def asString: String = "prod"
  }

  override def all: Set[PlayEnv] = Set(Dev, Stage, ProdEnvTesting, Prod)
}
