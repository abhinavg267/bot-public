package utils

import play.api.Logger
import services.notifications.MessagingService
import utils.FutureUtils.SafeFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

class LoggerWithNotification(private val name: String, private val messagingService: MessagingService) {
  val logger: Logger = Logger(name)
  def info(notify: Boolean)(message: String): Unit = {
    logger.info(message)
    if(notify) {
      Try {
        messagingService.sendMessage(message)
      }.catchLogAndSkip(Events.SendNotification(message))
    }
  }

  def error(notify: Boolean)(message: String): Unit = {
    logger.error(message)
    if(notify) {
      Try {
        messagingService.sendMessage(message)
      }.catchLogAndSkip(Events.SendNotification(message))
    }
  }

  def warn(notify: Boolean)(message: String): Unit = {
    logger.warn(message)
    if(notify) {
      Try {
        messagingService.sendMessage(message)
      }.catchLogAndSkip(Events.SendNotification(message))
    }
  }
}
