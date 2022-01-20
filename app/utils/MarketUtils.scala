package utils

import org.joda.time.DateTime

object MarketUtils {
  def isInsideMarketHour(date: DateTime): Boolean = {
    (date.getHourOfDay > 9 || (date.getHourOfDay == 9 && date.getMinuteOfHour >= 15)) &&
      (date.getHourOfDay < 15 || (date.getHourOfDay == 15 && date.getMinuteOfHour < 30))
  }
}
