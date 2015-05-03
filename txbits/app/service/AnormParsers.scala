// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

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
