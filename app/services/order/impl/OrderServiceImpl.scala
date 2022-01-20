package services.order.impl

import com.google.inject.{Inject, Singleton}
import models._
import models.status_and_types.OrderExecutionStatus.{OrderCreated, OrderExecuted, OrderExecutedManually, OrderSendFailed, OrderSendInProgress, OrderSent}
import models.status_and_types._
import org.joda.time.DateTime
import services.broker_apis.BrokerAPIProvider
import services.order.OrderService
import services.strategies.details.StrategyDetailsService
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery
import utils.CustomExceptions.{FailedToFetchOrderCompletionStatus, OrderCompletionDelay, OrderPlacementFailure}
import utils.DBQueryUtils.assertOneUpdate
import utils.DBUtils.{dateTimeDBMapping, dbRunNonTransactionally, dbRunTransactionally}
import utils.Events.MarkOrderExecutedManually
import utils.{Events, PriceUtils}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationLong
import scala.util.{Failure, Success, Try}

@Singleton
class OrderServiceImpl @Inject ()(strategyDetailsService: StrategyDetailsService,
                                  brokerAPIProvider: BrokerAPIProvider) extends OrderService {
  val orderQuery: TableQuery[Orders] = Orders.query
  val positionQuery: TableQuery[Positions] = Positions.query
  val orderAssociationQuery: TableQuery[OrderAssociations] = OrderAssociations.query

  override def createAnOrderDBIO(tradingSymbol: String, transactionType: TransactionType, orderType: OrderType,
                                 quantity: Int, productType: ProductType, triggerPrice: BigDecimal,
                                 orderPrice: Option[BigDecimal], strategyId: String, strategyCounter: Int,
                                 associatedOrder: Option[Int], triggerType: OrderTriggerType,
                                 now: DateTime): DBIO[Int] = {
    val orderTickPrice = orderPrice.map(PriceUtils.roundOffToTickPrice)
    val order = Order(id = -1, tradingSymbol, transactionType, orderType, quantity, productType, triggerPrice, orderTickPrice,
      executionPrice = None, strategyId, strategyCounter, OrderExecutionStatus.OrderCreated, brokersOrderId = None,
      triggerType, createdAt = now, updatedAt = now)

    for {
      newOrder <- orderQuery returning orderQuery += order

      _ <- if(associatedOrder.nonEmpty) {
        val orderAssociation = transactionType match {
          case TransactionType.Buy => OrderAssociation(newOrder.id, associatedOrder.get, quantity, now)
          case TransactionType.Sell => OrderAssociation(associatedOrder.get, newOrder.id, quantity, now)
        }
        (orderAssociationQuery += orderAssociation).map(_ => ())
      } else {
        DBIO.successful(())
      }

      _ <- strategyDetailsService.increaseOrderCount(strategyId, now)
    } yield newOrder.id
  }

  // returns order and open quantity
  override def getExecutedOpenOrders(strategyId: String, strategyCounter: Int): DBIO[Seq[(Order, Int)]] = {
    for {
      allOrder <- orderQuery.filter(r => r.strategyId === strategyId && r.strategyCounter === strategyCounter &&
        r.orderExecutionStatus.inSet(Set(OrderExecutedManually, OrderExecuted))).result
      allAssociation <- orderAssociationQuery.filter { r =>
        r.buyOrderId.inSet(allOrder.map(_.id)) || r.sellOrderId.inSet(allOrder.map(_.id))
      }.result
    } yield allOrder.flatMap { order =>
      order.transactionType match {
        case TransactionType.Buy =>
          val associations = allAssociation.filter(a => a.buyOrderId == order.id).toSet
          val soldQuantity: Int = associations.foldRight(0) { case (association, res) => res + association.quantity }
          if(order.quantity - soldQuantity <= 0) None
          else Some((order, order.quantity - soldQuantity))

        case TransactionType.Sell =>
          val associations = allAssociation.filter(a => a.sellOrderId == order.id).toSet
          val boughtQuantity: Int = associations.foldRight(0) { case (association, res) => res + association.quantity }
          if(order.quantity - boughtQuantity <= 0) None
          else Some((order, order.quantity - boughtQuantity))
      }
    }
  }

  override def getAllOrders(strategyId: String, strategyCounter: Int): DBIO[Seq[Order]] = {
    orderQuery.filter(r => r.strategyId === strategyId && r.strategyCounter === strategyCounter).result
  }

  override def sendCreatedOrders(now: DateTime): Future[Unit] = {
    for {
      createdOrders <- dbRunNonTransactionally(orderQuery.filter(_.orderExecutionStatus === (OrderCreated: OrderExecutionStatus)).result)
      _ <- createdOrders.headOption match {
        case Some(createdOrder) => for {
          broker <- dbRunNonTransactionally(strategyDetailsService.getBrokerForStrategyDBIO(createdOrder.strategyId, createdOrder.strategyCounter))
          brokerAPI = brokerAPIProvider.getBrokerAPI(broker)


          _ <- dbRunTransactionally(
            assertOneUpdate(Events.UpdateOrderSentStatus(createdOrder.id, OrderSendInProgress))(
              orderQuery.filter(r => r.id === createdOrder.id && r.orderExecutionStatus === (OrderCreated: OrderExecutionStatus))
                .map(r => (r.orderExecutionStatus, r.updatedAt))
                .update((OrderExecutionStatus.OrderSendInProgress, now))
            )
          )

          _ <- Try { brokerAPI.placeOrder(createdOrder) } match {
            case Failure(exception) =>
              dbRunTransactionally(
                assertOneUpdate(Events.UpdateOrderSentStatus(createdOrder.id, OrderSendFailed))(
                  orderQuery.filter(r => r.id === createdOrder.id && r.orderExecutionStatus === (OrderSendInProgress: OrderExecutionStatus))
                    .map(r => r.orderExecutionStatus).update(OrderSendFailed)
                )
              ).map {
                _ => throw OrderPlacementFailure(exception.getMessage, exception)
              }
            case Success(brokersOrderId) =>
              dbRunTransactionally(
                assertOneUpdate(Events.UpdateOrderSentStatus(createdOrder.id, OrderSent))(
                  orderQuery.filter(r => r.id === createdOrder.id && r.orderExecutionStatus === (OrderSendInProgress: OrderExecutionStatus))
                    .map(r => (r.orderExecutionStatus, r.brokersOrderId)).update((OrderSent, Some(brokersOrderId)))
                )
              )
          }
        } yield ()

        case None => Future.successful(Unit)
      }
    } yield ()
  }

  override def markSentOrdersAsExecuted(now: DateTime): Future[Unit] = {
    for {
      sentOrders <- dbRunTransactionally(orderQuery.filter(_.orderExecutionStatus === (OrderSent: OrderExecutionStatus)).result)
      _ <- Future.sequence(sentOrders.map { sentOrder =>
        for {
          broker <- dbRunNonTransactionally(strategyDetailsService.getBrokerForStrategyDBIO(sentOrder.strategyId, sentOrder.strategyCounter))
          brokerAPI = brokerAPIProvider.getBrokerAPI(broker)
          brokersOrderId = sentOrder.brokersOrderId.getOrElse(throw new Exception(s"Cannot find brokers order id for " +
            s"order with status $OrderSent. $sentOrder"))

          _ <- Try { brokerAPI.isOrderComplete(brokersOrderId, sentOrder.tradingSymbol) } match {
            case Failure(exception) => throw FailedToFetchOrderCompletionStatus(sentOrder.id, exception)

            case Success(isOrderCompleteResponse) =>
              if(isOrderCompleteResponse.isOrderComplete) {
                dbRunTransactionally(for {
                  _ <- addOrUpdatePosition(sentOrder, now)
                  _ <- orderQuery.filter(_.id === sentOrder.id).map(r => (r.orderExecutionStatus, r.executionPrice, r.updatedAt))
                    .update((OrderExecutionStatus.OrderExecuted, isOrderCompleteResponse.orderExecutionPrice, now))
                } yield ())
              }
              else if(now.isAfter(sentOrder.createdAt.plusSeconds(60)))
                throw OrderCompletionDelay(sentOrder.id, (now.getMillis - sentOrder.createdAt.getMillis).milliseconds)
              else Future.successful(())
          }
        } yield ()
      })
    } yield ()
  }

  override def markOrderExecutedManually(orderId: Int, brokersOrderId: String, executionPrice: BigDecimal,
                                         now: DateTime): Future[Order] = {
    dbRunTransactionally(
      for {
        order <- orderQuery.filter(_.id === orderId).take(1).result.headOption.map(_.getOrElse(
          throw new Exception(s"No order found with id $orderId")))
        _ <- addOrUpdatePosition(order, now)
        _ <- assertOneUpdate(MarkOrderExecutedManually(orderId))(
          orderQuery.filter(r => r.id === orderId && r.orderExecutionStatus.inSet(Set(OrderCreated, OrderSendFailed, OrderSent)))
            .map(r => (r.brokersOrderId, r.orderExecutionStatus, r.executionPrice, r.updatedAt))
            .update((Some(brokersOrderId), OrderExecutedManually, Some(executionPrice), now))
        )

        updatedOrder <- orderQuery.filter(_.id === orderId).take(1).result.headOption.map(_.getOrElse(
          throw new Exception(s"No order found with id $orderId")))
      } yield updatedOrder
    )
  }

  // PRIVATE METHODS
  private def addOrUpdatePosition(newOrder: Order, now: DateTime): DBIO[Unit] = {
    for {
      existingPositionOpt <- positionQuery.filter(r => r.tradingSymbol === newOrder.tradingSymbol &&
        r.strategyId === newOrder.strategyId && r.strategyCounter === newOrder.strategyCounter
      ).take(1).result.headOption

      rowsAffected <- existingPositionOpt match {
        case Some(existingPosition) =>
          val updatedQuantity = existingPosition.openQuantity + newOrder.signedQuantity
          positionQuery.filter(_.id === existingPosition.id).map(r => (r.openQuantity, r.updatedAt))
            .update((updatedQuantity, now))

        case None =>
          val newPosition = Position(-1, newOrder.tradingSymbol, newOrder.signedQuantity,
            newOrder.strategyId, newOrder.strategyCounter, now, now)
          positionQuery += newPosition
      }
    } yield assert(rowsAffected == 1)
  }
}
