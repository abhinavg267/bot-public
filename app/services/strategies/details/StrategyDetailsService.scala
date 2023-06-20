package services.strategies.details

import com.google.inject.ImplementedBy
import models.StrategyDetail
import models.status_and_types.{Broker, StrategyId, StrategyState}
import models.wrappers.StrategyStateVariables
import org.joda.time.DateTime
import services.strategies.details.impl.StrategyDetailsServiceImpl
import slick.dbio.DBIO

@ImplementedBy(classOf[StrategyDetailsServiceImpl])
trait StrategyDetailsService {
  def addStrategyDetailsOnStrategyStartup(strategyId: String, broker: Broker, maxOrderCountPerCounter: Int,
                                          strategyStateVariables: Option[StrategyStateVariables],
                                          now: DateTime): DBIO[StrategyDetail]

  def increaseOrderCount(strategyId: String, now: DateTime): DBIO[Unit]

  def updateStrategyState(strategyId: StrategyId, strategyState: StrategyState, now: DateTime): DBIO[Unit]

  def updateStrategyStateVariables(strategyId: StrategyId, strategyStateVariables: StrategyStateVariables,
                                   now: DateTime): DBIO[Unit]

  def getBrokerForStrategyDBIO(strategyId: String, strategyCounter: Int): DBIO[Broker]

  def getLatestStrategyDetailByStrategyId(strategyId: String) : DBIO[StrategyDetail]

  def getLatestStrategyDetailByStrategyIdOpt(strategyId: String) : DBIO[Option[StrategyDetail]]
}
