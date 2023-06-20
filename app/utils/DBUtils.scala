package utils

import org.joda.time.DateTime
import slick.ast.BaseTypedType
import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc
import slick.jdbc.JdbcType
import slick.jdbc.PostgresProfile.MappedColumnType
import slick.jdbc.PostgresProfile.api._

import java.sql.Timestamp
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object DBUtils {
  private val db: jdbc.PostgresProfile.backend.Database = Database.forConfig(QualifiedConfiguration.getQualifiedPath("psql_dc"))
  def dbRunTransactionally[R](a: DBIOAction[R, NoStream, Effect.All]): Future[R] = {
    db.run(a.transactionally).recover { case dbError =>
      throw dbError
    }
  }

  def dbRunNonTransactionally[R](a: DBIOAction[R, NoStream, Effect.All]): Future[R] = {
    db.run(a).recover { case dbError =>
      throw dbError
    }
  }

  implicit def dateTimeDBMapping: JdbcType[DateTime] with BaseTypedType[DateTime] =
    MappedColumnType.base[DateTime, Timestamp](
      dt => new Timestamp(dt.getMillis),
      ts => new DateTime(ts.getTime)
    )
}
