package utils

import play.api.libs.json.{Format, JsResult, JsString, JsValue}
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.MappedColumnType
import slick.jdbc.PostgresProfile.api._

import scala.reflect.ClassTag

trait WithAsString {
  override def toString: String = asString
  def asString: String
}

trait StringCompanion[T <: WithAsString] {
  def all: Set[T]
  def fromStringOpt(str: String): Option[T] = all.collectFirst {
    case ele if ele.asString == str => ele
  }

  def fromString(str: String)(implicit classTag: ClassTag[T]): T = fromStringOpt(str).getOrElse(
    throw new Exception(s"String $str is not a defined type of ${classTag.runtimeClass.getName}"))
}

trait WithDBMapping[T <: WithAsString]  {
  self: StringCompanion[T] =>

  implicit def dbMapping(implicit classTag: ClassTag[T]): JdbcType[T] with BaseTypedType[T] =
    MappedColumnType.base[T, String](_.asString, fromString)
}

trait WithJsonMapping[T <: WithAsString]  {
  self: StringCompanion[T] =>

  implicit def jsonMapping(implicit classTag: ClassTag[T]): Format[T] = new Format[T] {
    override def writes(o: T): JsValue = JsString(o.asString)
    override def reads(json: JsValue): JsResult[T] = json.validate[String].map(fromString)
  }
}