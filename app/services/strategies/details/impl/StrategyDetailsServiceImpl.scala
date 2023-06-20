package services.strategies.details.impl

import models.status_and_types.StrategyState.{Closed, Running}
import models.status_and_types.{Broker, StrategyId, StrategyState}
import models.wrappers.StrategyStateVariables
import models.{StrategyDetail, StrategyDetails}
import org.joda.time.DateTime
import services.strategies.details.StrategyDetailsService
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery
import utils.DBQueryUtils.assertOneUpdate
import utils.DBUtils.dateTimeDBMapping
import utils.Events
import utils.Events.StartStrategy

import scala.concurrent.ExecutionContext.Implicits.global

class StrategyDetailsServiceImpl extends StrategyDetailsService {
  private val strategyDetailsQuery: TableQuery[StrategyDetails] = StrategyDetails.query

  override def addStrategyDetailsOnStrategyStartup(strategyId: String, broker: Broker, maxOrderCountPerCounter: Int,
                                                   strategyStateVariables: Option[StrategyStateVariables],
                                                   now: DateTime): DBIO[StrategyDetail] = for {
    existingStrategyDetailOpt <- getLatestStrategyDetailByStrategyIdOpt(strategyId)
    newRow <- existingStrategyDetailOpt match {
      case Some(existingStrategyDetail) =>
        for {
          newStrategyDetail <- strategyDetailsQuery returning strategyDetailsQuery += StrategyDetail(-1, strategyId,
            existingStrategyDetail.counter + 1, broker, orderCount = 0, maxOrderCount = maxOrderCountPerCounter,
            strategyState = Running, strategyStateVariables, now, now, supersededBy = None)

          _ <- assertOneUpdate(StartStrategy(newStrategyDetail.strategyId))(
            strategyDetailsQuery.filter(r => r.id === existingStrategyDetail.strategyDetailId && (r.strategyState === (Closed: StrategyState)))
              .map(r => (r.updatedAt, r.supersededBy))
              .update((now, Some(newStrategyDetail.strategyDetailId)))
          )
        } yield {
          newStrategyDetail
        }

      case None => strategyDetailsQuery returning strategyDetailsQuery += StrategyDetail(-1, strategyId, 1, broker,
        orderCount = 0, maxOrderCount = maxOrderCountPerCounter, strategyState = Running, strategyStateVariables, now,
        now, supersededBy = None)
    }
  } yield newRow

  override def increaseOrderCount(strategyId: String, now: DateTime): DBIO[Unit] = {
    for {
      latestStrategyDetail <- getLatestStrategyDetailByStrategyId(strategyId)
      _ <- assertOneUpdate(Events.IncreaseOrderCount(strategyId))(strategyDetailsQuery.filter(r => r.strategyId === strategyId &&
        r.orderCount === latestStrategyDetail.orderCount && r.supersededBy.isEmpty).map(r => (r.orderCount, r.updatedAt))
        .update((latestStrategyDetail.orderCount + 1, now)))
    } yield {
      assert(latestStrategyDetail.orderCount + 1 <= latestStrategyDetail.maxOrderCount, s"Order count cannot be " +
        s"more than max Order count defined for strategy: $latestStrategyDetail")
    }
  }

  override def updateStrategyState(strategyId: StrategyId, strategyState: StrategyState, now: DateTime): DBIO[Unit] = {
    val allowedCurrentStrategyStates: Set[StrategyState] = strategyState match {
      case Running => Set.empty[StrategyState]
      case Closed => Set(Running)
    }

    assertOneUpdate(Events.UpdateStrategyState(strategyId.asString, strategyState))(strategyDetailsQuery.filter(r => r.strategyId === strategyId.asString &&
      r.strategyState.inSet(allowedCurrentStrategyStates) && r.supersededBy.isEmpty).map(r => (r.strategyState, r.updatedAt))
      .update((strategyState, now)))
  }

  override def updateStrategyStateVariables(strategyId: StrategyId, strategyStateVariables: StrategyStateVariables,
                                            now: DateTime): DBIO[Unit] = {
    assertOneUpdate(Events.UpdateStrategyStateVariables(strategyId.asString, strategyStateVariables))(
      strategyDetailsQuery.filter(r => r.strategyId === strategyId.asString && r.supersededBy.isEmpty)
        .map(r => (r.strategyStateVariables, r.updatedAt))
        .update((Some(strategyStateVariables), now)))
  }

  override def getBrokerForStrategyDBIO(strategyId: String, strategyCounter: Int): DBIO[Broker] = {
    for {
      res <- strategyDetailsQuery.filter(r => r.strategyId === strategyId && r.counter === strategyCounter)
        .take(1).result.headOption
    } yield res.getOrElse(throw new Exception(s"Cannot find strategy details with " +
      s"id $strategyId and counter: $strategyCounter")).broker
  }

  override def getLatestStrategyDetailByStrategyId(strategyId: String) : DBIO[StrategyDetail] = {
    getLatestStrategyDetailByStrategyIdOpt(strategyId).map(_.getOrElse(throw new Exception(s"Cannot find strategy " +
      s"details with id: $strategyId, with superseded by null")))
  }

  override def getLatestStrategyDetailByStrategyIdOpt(strategyId: String) : DBIO[Option[StrategyDetail]] = {
    strategyDetailsQuery.filter(r => r.strategyId === strategyId && r.supersededBy.isEmpty).take(1).result.headOption
  }
}
