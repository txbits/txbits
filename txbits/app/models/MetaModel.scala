// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package models

import play.api.db.DB
import play.api.Play.current
import anorm._
import anorm.SqlParser._
import anorm.~
import service.sql.frontend
import service.sql.misc
import play.api.libs.json.Json

case class DwFee(currency: String, method: String, depositConstant: String, depositLinear: String, withdrawConstant: String, withdrawLinear: String)

object DwFee {
  implicit val writes = Json.writes[DwFee]
  implicit val format = Json.format[DwFee]
}

case class DwLimit(currency: String, limit_min: String, limit_max: String)

object DwLimit {
  implicit val writes = Json.writes[DwLimit]
  implicit val format = Json.format[DwLimit]
}

class MetaModel(val db: String = "default") {

  import globals.bigDecimalColumn

  val currencies = DB.withConnection(db)(implicit c => {
    frontend.getCurrencies().map(_[String]("currency")).toList
  })

  val validPairs = DB.withConnection(db)(implicit c => {
    frontend.getPairs().map(row => (row[String]("base"), row[String]("counter"), row[Boolean]("active"), row[BigDecimal]("limit_min"))).toList
  })

  val allPairsJson = validPairs.map(pair => Json.obj("base" -> pair._1, "counter" -> pair._2))

  val activeMarkets = validPairs.map {
    case (base: String, counter: String, active: Boolean, minAmount: BigDecimal) =>
      (base, counter) -> (active, minAmount)
  }.toMap

  val dwFees = DB.withConnection(db)(implicit c => {
    frontend.dwFees().map(row =>
      DwFee(
        row[String]("currency"),
        row[String]("method"),
        row[BigDecimal]("deposit_constant").bigDecimal.toPlainString,
        row[BigDecimal]("deposit_linear").bigDecimal.toPlainString,
        row[BigDecimal]("withdraw_constant").bigDecimal.toPlainString,
        row[BigDecimal]("withdraw_linear").bigDecimal.toPlainString
      )
    ).toList
  })

  val tradeFees = DB.withConnection(db)(implicit c => {
    frontend.tradeFees().map(row =>
      row[BigDecimal]("linear")
    ).head
  })

  val dwLimits = DB.withConnection(db)(implicit c => {
    frontend.dwLimits().map(row =>
      row[String]("currency") ->
        DwLimit(
          row[String]("currency"),
          row[BigDecimal]("limit_min").bigDecimal.toPlainString,
          row[BigDecimal]("limit_max").bigDecimal.toPlainString
        )
    ).toMap
  })

  val getRequiredConfirmations = DB.withConnection(db)(implicit c => {
    frontend.getRequiredConfirmations().map(row =>
      row[String]("currency") -> row[Int]("min_deposit_confirmations").toString
    ).toMap
  })

  // privileged api

  def clean() = DB.withConnection(db)(implicit c =>
    misc.metaClean.execute()
  )

}