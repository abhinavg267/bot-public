package utils

import scala.collection.JavaConverters
import java.util

object JavaConversionUtils {
  def toSeq[T](javaList: util.List[T]): Seq[T] = {
    JavaConverters.asScalaIteratorConverter(javaList.iterator).asScala.toSeq
  }

  def toList[T](javaList: util.List[T]): List[T] = {
    JavaConverters.asScalaIteratorConverter(javaList.iterator).asScala.toList
  }
}
