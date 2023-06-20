package controllers

import com.google.inject.Inject
import controllers.HomeController.{CreateNewOrderManuallyRequestDTO, MarkOrderExecutedManuallyRequestDTO}
import models.Order
import models.status_and_types.{Broker, OrderTriggerType, OrderType, ProductType, TransactionType}
import org.joda.time.DateTime
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json, OFormat}
import play.api.mvc.Results.Unauthorized
import play.api.mvc._
import services.broker_apis.{BrokerAPI, BrokerAPIProvider}
import services.data_providers.DataProvider
import services.order.OrderService
import services.schedulers.TaskScheduler
import utils.DBUtils.dbRunTransactionally
import utils.JsonFormatUtils.dateTimeReadableJsonMapping
import utils.QualifiedConfiguration

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class HomeController @Inject()(val controllerComponents: ControllerComponents,
                               dataProvider: DataProvider,
                               brokerAPIProvider: BrokerAPIProvider,
                               orderService: OrderService,
                               taskScheduler: TaskScheduler) extends BaseController {
  val brokerAPI: BrokerAPI = brokerAPIProvider.getBrokerAPI(Broker.Zerodha)

  def index(): Action[AnyContent] = Action { implicit req =>
    WithBasicAuthentication {
      Ok(views.html.index())
    }
  }

  def initiateSession(): Action[AnyContent] = Action { implicit req =>
    WithBasicAuthentication {
      val redirectUrl: String = s"${brokerAPI.getLoginUrl}"
      Redirect(redirectUrl, Map(("redirect_params", Seq("target=BOT"))))
    }
  }

  def startSession(statusStr: String, requestTokenStr: String, target: String): Action[AnyContent] = Action { implicit req =>
    WithBasicAuthentication {
      if(statusStr != "success") throw new Exception(s"Could not create a session")

      if(target == "BOT") {
        brokerAPI.createSession(requestToken = requestTokenStr)
        taskScheduler.triggerAllSchedules()
        Ok(Json.obj(("status", "SUCCESS"), ("message", "New trading session started successfully!")))
      } else {
        BadRequest(s"Cannot handle $target for starting a new session")
      }
    }
  }

  def markOrderExecutedManually(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val now = DateTime.now()
    WithBasicAuthentication {
      request.body.validate[MarkOrderExecutedManuallyRequestDTO] match {
        case JsSuccess(requestDTO, _) => orderService.markOrderExecutedManually(requestDTO.orderId,
          requestDTO.brokersOrderId, requestDTO.executionPrice, now).map { updatedOrder =>
          Ok(Json.obj(("status", "SUCCESS"), ("message", "Order updated successfully!"),
            ("order", Json.toJson(updatedOrder)(Json.format[Order]))))
        }

        case JsError(errors) => Future.successful(BadRequest(Json.obj(("status", "FAILURE"),
          ("message", s"Cannot parse ${request.body} as $MarkOrderExecutedManuallyRequestDTO!"),
          ("errors", s"$errors"))))
      }
    }
  }

  def createNewOrderManually(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    WithBasicAuthentication {
      request.body.validate[CreateNewOrderManuallyRequestDTO] match {
        case JsSuccess(requestDTO, _) => dbRunTransactionally(orderService.createAnOrderDBIO(requestDTO.tradingSymbol,
          requestDTO.transactionType, requestDTO.orderType, requestDTO.quantity, requestDTO.productType,
          requestDTO.triggerPrice, requestDTO.orderPrice, requestDTO.strategyId, requestDTO.strategyCounter,
          requestDTO.associatedOrder, requestDTO.triggerType, requestDTO.orderCreationTime).map { updatedRows =>
          Ok(Json.obj(("status", "SUCCESS"), ("message", "Order updated successfully!"),
            ("addedRows", updatedRows)))
        })

        case JsError(errors) => Future.successful(BadRequest(Json.obj(("status", "FAILURE"),
          ("message", s"Cannot parse ${request.body} as $MarkOrderExecutedManuallyRequestDTO!"),
          ("errors", s"$errors"))))
      }
    }
  }

  def getLTP(sym: String): Action[AnyContent] = Action { implicit req =>
    WithBasicAuthentication {
      val ltp = dataProvider.getLTP(sym)
      Ok(Json.obj(("status", "SUCCESS"), ("data", ltp)))
    }
  }

  def kill(): Action[AnyContent] = Action { implicit req =>
    System.exit(0)
    Ok(Json.obj(("status", "SUCCESS"), ("message", "Killed the service successfully!")))
  }
}

object HomeController {
  case class MarkOrderExecutedManuallyRequestDTO(orderId: Int, brokersOrderId: String,
                                                 executionPrice: BigDecimal)
  object MarkOrderExecutedManuallyRequestDTO {
    implicit val formats: OFormat[MarkOrderExecutedManuallyRequestDTO] = Json.format[MarkOrderExecutedManuallyRequestDTO]
  }

  case class CreateNewOrderManuallyRequestDTO(tradingSymbol: String, transactionType: TransactionType, orderType: OrderType,
                                              quantity: Int, productType: ProductType, triggerPrice: BigDecimal,
                                              orderPrice: Option[BigDecimal], strategyId: String, strategyCounter: Int,
                                              associatedOrder: Option[Int], triggerType: OrderTriggerType,
                                              orderCreationTime: DateTime)
  object CreateNewOrderManuallyRequestDTO {
    implicit val formats: OFormat[CreateNewOrderManuallyRequestDTO] = Json.format[CreateNewOrderManuallyRequestDTO]
  }
}

object WithBasicAuthentication {
  val username: String = QualifiedConfiguration.getString("authenticated.username")
  val password: String = QualifiedConfiguration.getString("authenticated.password")
  val authorizedKey: String = s"Basic ${Base64.getEncoder.encodeToString(s"$username:$password".getBytes(StandardCharsets.UTF_8))}"

  def apply[T](f: => Result)(implicit request: Request[T]): Result = {
    request.headers.get("Authorization") match {
      case Some(authorizationKeyFromRequest) =>
        if(authorizationKeyFromRequest == authorizedKey) f
        else Unauthorized(s"Unauthorized credentials, please try again!").withHeaders(("WWW-Authenticate", "Basic"))
      case None => Unauthorized(s"Please provide credentials in your browser popup!").withHeaders(("WWW-Authenticate", "Basic"))
    }
  }

  def apply[T](f: => Future[Result])(implicit request: Request[T]): Future[Result] = {
    request.headers.get("Authorization") match {
      case Some(authorizationKeyFromRequest) =>
        if(authorizationKeyFromRequest == authorizedKey) f
        else Future.successful(Unauthorized(s"Unauthorized credentials, please try again!").withHeaders(("WWW-Authenticate", "Basic")))
      case None => Future.successful(Unauthorized(s"Please provide credentials in your browser popup!").withHeaders(("WWW-Authenticate", "Basic")))
    }
  }
}
