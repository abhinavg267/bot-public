package utils

object MathUtils {
  def max(a: BigDecimal, b: BigDecimal): BigDecimal = {
    if(a>b) a 
    else b
  }
}
