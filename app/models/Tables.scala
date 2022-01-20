package models
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.meta.MTable
import utils.DBUtils.dbRunTransactionally

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Tables {
  private val tableQueries = List(
    Orders.query,
    Positions.query,
    StrategyDetails.query,
    OrderAssociations.query
  )

  def ensureTableAvailability(): Future[Unit] = {
    val existing = dbRunTransactionally(MTable.getTables)
    existing.flatMap( v => {
      val names = v.map(mt => mt.name.name)
      val createIfNotExist = tableQueries.filter( table =>
        !names.contains(table.baseTableRow.tableName)).map(_.schema.create)
      dbRunTransactionally(DBIO.sequence(createIfNotExist))
    }).map(_ => ())
  }
}
