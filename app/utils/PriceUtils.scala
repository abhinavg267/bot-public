package utils

object PriceUtils {
  // 12.3453 - 0.0453
  def roundOffToTickPrice(price: BigDecimal): BigDecimal = {
    val tickPrice: BigDecimal = 0.05
    MathUtils.max(tickPrice, price - price % tickPrice)
  }
}
