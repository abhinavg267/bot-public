package utils

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.annotation.tailrec

object TradingSymbolUtilities {
  val niftyTradingSymbol: String = "NSE:NIFTY 50"
  val bankNiftyTradingSymbol: String = "NSE:NIFTY BANK"
  val niftyOptionPrefix: String = "NFO:NIFTY"
  val bankNiftyOptionPrefix: String = "NFO:BANKNIFTY"

  def getNiftyATMPrice(niftyLTP: BigDecimal): Int = {
    if(niftyLTP % 50 <= 25) (niftyLTP - niftyLTP % 50).toInt
    else (niftyLTP + (50 - (niftyLTP % 50))).toInt
  }

  def getBankNiftyATMPrice(bankNiftyLTP: BigDecimal): Int = {
    if(bankNiftyLTP % 100 <= 50) (bankNiftyLTP - bankNiftyLTP % 100).toInt
    else (bankNiftyLTP + (100 - (bankNiftyLTP % 100))).toInt
  }

  def getWeeklyDateOfExpiry(date: DateTime): DateTime = {
    def daysFromExpiry(d: DateTime): Int = ((4 - d.dayOfWeek().get()) % 7 + 7) % 7
    getActiveTradingDay(date.plusDays(daysFromExpiry(date)))
  }

  val nseHolidays: Set[DateTime] = QualifiedConfiguration.getStringList("nse_holidays").toSet.map { dateString: String =>
    DateTime.parse(dateString, DateTimeFormat.forPattern("dd-MMM-yyyy"))
  }

  @tailrec
  def getActiveTradingDay(dateTime: DateTime): DateTime = {
    if(nseHolidays.exists(_.isEqual(dateTime.withTimeAtStartOfDay()))) getActiveTradingDay(dateTime.minusDays(1))
    else dateTime
  }

  /* Trading Symbol = [Underlying Asset] + [expiry as YYMMDD] + [Strike Price] + [CE/PE]
   * for expiry on 16th September 2021 => 21916 => NIFTY2191617400CE
   */
  def getOptionTradingSymbol(asset: String, expiry: DateTime, strikePrice: Int, optionType: String): String = {
    val weeklyExpiryMonthStr: String =
      if(expiry.getMonthOfYear < 10) expiry.toString("M")
      else expiry.toString("MMM").charAt(0).toUpper.toString

    val expiryStr: String =
      if(getWeeklyDateOfExpiry(expiry.plusDays(7)).getMonthOfYear != expiry.getMonthOfYear)
        expiry.toString("yyMMM").toUpperCase()
      else expiry.toString("yy") + weeklyExpiryMonthStr + expiry.toString("dd")

    s"$asset$expiryStr$strikePrice$optionType"
  }
}
