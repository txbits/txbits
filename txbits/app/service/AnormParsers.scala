package service

import anorm._
import anorm.MetaDataItem
import anorm.TypeDoesNotMatch

object AnormParsers {

  // handle parsing 2d arrays of numbers
  implicit val rowToBigDecimalArrayArray: Column[Array[Array[java.math.BigDecimal]]] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case o: java.sql.Array => Right(o.getArray().asInstanceOf[Array[Array[java.math.BigDecimal]]])
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass))
    }
  }
}
