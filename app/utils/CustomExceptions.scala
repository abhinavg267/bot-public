package utils

import scala.concurrent.duration.FiniteDuration

object CustomExceptions {
  case class OrderPlacementFailure(message: String, cause: Throwable)
    extends Exception(message, cause)

  case class OrderCompletionDelay(orderId: Int, delay: FiniteDuration)
    extends Exception(s"Order $orderId is not completed even after $delay")

  case class FailedToFetchOrderCompletionStatus(orderId: Int, cause: Throwable)
    extends Exception(s"Could not fetch order completion status of order $orderId due to error: ${cause.getMessage}", cause)
}
