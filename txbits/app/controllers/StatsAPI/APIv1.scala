// TxBits - An open source Bitcoin and crypto currency exchange
// Copyright (C) 2014-2015  Viktor Stanchev & Kirk Zathey
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package controllers.StatsAPI

import javax.inject.Inject

import globals._
import play.api.i18n.I18nSupport
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.mvc.{ Action, Controller }
import play.api.libs.iteratee.{ Iteratee, Concurrent }
import play.api.i18n.MessagesApi
import scala.collection.mutable
import play.api.libs.iteratee.Concurrent.Channel
import org.joda.time.DateTime
import play.api.db.DB
import java.sql.Timestamp
import models.Match
import akka.actor.Cancellable
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import org.postgresql.util.PSQLException
import anorm._
import scala.Some
import play.api.libs.json.JsObject
import java.util.Date

case class Ticker(first: String, low: String, high: String, last: String, volume: String, base: String, counter: String)
object Ticker {
  implicit val writes = Json.writes[Ticker]
  implicit val format = Json.format[Ticker]
}
case class TickerHistory(date: DateTime, first: BigDecimal, low: BigDecimal, high: BigDecimal, last: BigDecimal, volume: BigDecimal)
object TickerHistory {
  implicit val writes = Json.writes[TickerHistory]
  implicit val format = Json.format[TickerHistory]
}

// DON'T DO AUTHENTICATED ACTIONS OVER WEBSOCKETS UNTIL SOMEONE CAN VERIFY THAT THIS IS A SAFE THING TO DO

class APIv1 @Inject() (val messagesApi: MessagesApi) extends Controller with I18nSupport {
  import APIv1._

  val channels = mutable.Set[Channel[String]]()
  var lastMatchDatetime: DateTime = new DateTime(0)
  var lastMatchForPair = mutable.Map[String, JsObject]()

  var cancellable: Option[Cancellable] = None
  val DefaultInterval = 1
  val tickerInterval = "txbits.tickerInterval.sec"

  def onStart() {
    val i = current.configuration.getInt(tickerInterval).getOrElse(DefaultInterval)

  }

  def onStop() {
    cancellable.map(_.cancel())
  }

  def ticker = Action {
    Ok(Json.toJson(tickerFromDb))
  }

  def chart(base: String, counter: String) = Action {
    if (globals.metaModel.activeMarkets.contains(base, counter)) {
      Ok(Json.toJson(chartFromDB(base, counter)))
    } else {
      BadRequest(Json.obj("message" -> "Invalid pair."))
    }
  }
}

object APIv1 {
  def tickerFromDb = DB.withConnection(masterDB) { implicit c =>
    globals.metaModel.validPairs.flatMap {
      case (base, counter, active, minAmount) =>
        val res: List[Seq[JsNumber]] = chartFromDB(base, counter)
        if (!res.isEmpty) {
          val ticker = play.api.cache.Cache.getOrElse("%s.%s.ticker".format(base, counter)) {
            Ticker(
              res.head(1).value.toString(),
              res.map { _(3) }.reduce { (num1, num2) => if (num1.value < num2.value) num1 else num2 }.toString(),
              res.map { _(2) }.reduce { (num1, num2) => if (num1.value > num2.value) num1 else num2 }.toString(),
              res.last(4).value.toString(),
              res.map { _(5) }.reduce { (num1, num2) => JsNumber(num1.value + num2.value) }.toString(),
              base,
              counter
            )
          }
          Some(ticker)
        } else {
          None
        }
    }
  }

  def chartFromDB(base: String, counter: String) = DB.withConnection(masterDB) { implicit c =>
    play.api.cache.Cache.getOrElse("%s.%s.stats".format(base, counter)) {
      SQL""" select * from chart_from_db($base, $counter)"""().filter(row =>
        row[Option[Date]]("start_of_period").isDefined).map(row => {
        // We want a json array because that's what the graphing api understands
        // Format: Date,Open,High,Low,Close,Volume

        Seq(
          JsNumber(row[Option[Date]]("start_of_period").get.getTime),
          JsNumber(row[Option[BigDecimal]]("open").get),
          JsNumber(row[Option[BigDecimal]]("high").get),
          JsNumber(row[Option[BigDecimal]]("low").get),
          JsNumber(row[Option[BigDecimal]]("close").get),
          JsNumber(row[Option[BigDecimal]]("volume").get)
        )

      }).toList
    }
  }
}
