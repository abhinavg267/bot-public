package services.notifications

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.ws._
import utils.LoggingUtils.logger
import utils.QualifiedConfiguration

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@ImplementedBy(classOf[MessagingServiceImpl])
trait MessagingService {
  def sendMessage(message: String): Future[Unit]
}

@Singleton
class MessagingServiceImpl @Inject()(wsClient: WSClient) extends MessagingService {
  val botAPI: String = QualifiedConfiguration.getString("telegram_bot.url")
  val botToken: String = QualifiedConfiguration.getString("telegram_bot.bot_token")
  val chatId: String = QualifiedConfiguration.getString("telegram_bot.chat_id")

  override def sendMessage(message: String): Future[Unit] = {
    val url = botAPI.replace(":bot_token", botToken).replace(":chat_id", chatId).replace(":message", message)
    wsClient.url(url).get().map { response =>
      if(response.status != 200) logger.warn(s"Failed to send message to telegram. ErrorCode: ${response.status}")
    }
  }
}

