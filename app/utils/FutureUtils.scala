package utils

import utils.LoggingUtils.logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object FutureUtils {
  implicit class SafeFutures[T](t: Try[Future[T]]) {
    def safely(implicit ec: ExecutionContext): Future[Either[Throwable, T]] = {
      t match {
        case Failure(exception) => Future.successful(Left(exception))
        case Success(future) => future.map(Right(_)).recover { case th =>
          Left(th)
        }
      }
    }

    def catchLogAndSkip(event: Event)(implicit ec: ExecutionContext): Future[Option[T]] = {
      t match {
        case Failure(exception) =>
          logger.error(s"${exception.getMessage}", exception)
          Future.successful(None)
        case Success(future) => future.map(Some(_)).recover { case exception =>
          logger.error(s"${exception.getMessage} for event: $event", exception)
          None
        }
      }
    }
  }

  def runSequentially[A, B](values: Seq[A], default: Future[Seq[B]])(f: A => Future[B])(implicit ec: ExecutionContext): Future[Seq[B]] = {
    values.foldRight(default) { case (value, res) =>
      for {
        a <- res
        b <- f(value)
      } yield a :+ b
    }
  }
}
