package utils

import slick.dbio.{DBIO, Effect, NoStream}
import slick.jdbc.PostgresProfile.ProfileAction

import scala.concurrent.ExecutionContext.Implicits.global

object DBQueryUtils {
  def assertOneUpdate(event: Event)(f: => ProfileAction[Int, NoStream, Effect.Write]): DBIO[Unit] = {
    assertNUpdate(1, event)(f)
  }

  def assertNUpdate(n: Int, event: Event)(f: => ProfileAction[Int, NoStream, Effect.Write]): DBIO[Unit] = {
    for {
      rowsUpdated <- f
    } yield assert(rowsUpdated == n, s"Query is not updating expected $n rows, instead it is updating $rowsUpdated for event $event")
  }
}
