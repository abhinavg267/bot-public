package utils

import org.joda.time.{DateTime, DateTimeZone}

object DateTimeUtils {
  def convertTimeToDateTime(time: DateTime, now: DateTime): DateTime = {
    time.plus(now.withZone(DateTimeZone.UTC).withTimeAtStartOfDay().getMillis)
  }
}
