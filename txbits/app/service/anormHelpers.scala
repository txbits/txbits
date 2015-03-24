package service

import anorm.{ Column, TypeDoesNotMatch, MetaDataItem }
import org.joda.time.DateTime

object anormHelpers {
  // handles parsing bigDecimal columns
  // JDBC always gives us a java BigDecimal and we have to convert it into a scala one
  implicit val bigDecimalColumn = anorm.Column[BigDecimal] { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case v: java.math.BigDecimal => Right(BigDecimal(v))
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to BigDecimal for column " + qualified))
    }
  }

  // handles parsing timestamps
  implicit val timestampColumn = anorm.Column[DateTime] { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case v: java.sql.Timestamp => Right(new DateTime(v))
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to TimeStamp for column " + qualified))
    }
  }

  // handles parsing integers
  implicit val integerColumn = anorm.Column[Integer] { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case v: java.lang.Integer => Right(new Integer(v))
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to Integer for column " + qualified))
    }
  }

  // handles parsing symbols
  implicit val symbolColumn = anorm.Column[Symbol] { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case v: String => Right(Symbol(v))
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass + " to Symbol for column " + qualified))
    }
  }

  // handle parsing 2d arrays of numbers
  implicit val rowToBigDecimalArrayArray: Column[Array[Array[java.math.BigDecimal]]] = Column.nonNull { (value, meta) =>
    val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case o: java.sql.Array => Right(o.getArray().asInstanceOf[Array[Array[java.math.BigDecimal]]])
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass))
    }
  }
}
