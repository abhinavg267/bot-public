package utils

import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigRenderOptions}
import models.status_and_types.PlayEnv
import play.api.libs.json.{JsValue, Json}

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.util.{Failure, Success, Try}


object QualifiedConfiguration {
  private val config: Config = ConfigFactory.load()
  val playEnv: PlayEnv = PlayEnv.fromString(config.getString("play_env"))

  private def getWithOrWithoutServerName[T](path: String)(pathToParsed: String => T): T = {
    Try {
      pathToParsed(s"$playEnv.$path")
    } match {
      case Failure(_: ConfigException.Missing) => pathToParsed(s"$path")
      case Failure(exception) => throw exception
      case Success(value) => value
    }
  }

  def getQualifiedPath(path: String): String = {
    val res =
      if(config.hasPath(s"$playEnv.$path")) s"$playEnv.$path"
      else path
    println(s"Qualified path for $path: $res and not $playEnv.$path")
    res
  }

  def getString(path: String): String = getWithOrWithoutServerName(path)(config.getString)

  def getStringList(path: String): List[String] = getWithOrWithoutServerName(path) { parsedPath =>
    JavaConversionUtils.toList(config.getStringList(parsedPath))
  }

  def getJson(path: String): JsValue = getWithOrWithoutServerName(path) { qualifiedPath =>
    val jsonString: String = config.getObject(qualifiedPath).render(ConfigRenderOptions.concise())
    Json.parse(jsonString)
  }
}
