package utils

import org.joda.time.DateTime
import play.api.libs.json.{Format, JsResult, JsString, JsValue}

object JsonFormatUtils {
  implicit val dateTimeReadableJsonMapping: Format[DateTime] = new Format[DateTime] {
    override def writes(o: DateTime): JsValue = JsString(o.toString())
    override def reads(json: JsValue): JsResult[DateTime] = json.validate[String].map(DateTime.parse)
  }
}
