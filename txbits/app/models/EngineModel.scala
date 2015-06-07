// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package models

import anorm._
import play.api.db.DB
import play.api.Play.current
import anorm.SqlParser._
import wallet.Wallet
import org.joda.time.DateTime
import securesocial.core.Token
import play.api.libs.json.Json
import java.sql.Timestamp
import service.sql.frontend
import service.sql.misc

class EngineModel(val db: String = "default") {

  import service.AnormParsers.rowToBigDecimalArrayArray

  // privileged apis

  def clean() = DB.withConnection(db)(implicit c =>
    misc.cleanForTest.execute()
  )

  def testSetup() = DB.withConnection(db)(implicit c =>
    misc.setupForTest.execute()
  )

  def setFees(currency: String, method: String, depositConstant: BigDecimal, depositLinear: BigDecimal, withdrawConstant: BigDecimal, withdrawLinear: BigDecimal) = DB.withConnection(db)(implicit c =>
    misc.setFees.on(
      'currency -> currency,
      'method -> method,
      'depositConstant -> depositConstant.bigDecimal,
      'depositLinear -> depositLinear.bigDecimal,
      'withdrawConstant -> withdrawConstant.bigDecimal,
      'withdrawLinear -> withdrawLinear.bigDecimal
    ).execute()
  )

  // regular apis

  def flushMarketCaches(base: String, counter: String) {
    val caches = List("orders", "trades", "stats", "ticker")
    caches.foreach { c =>
      play.api.cache.Cache.remove("%s.%s.%s".format(base, counter, c))
    }
  }

  def balance(uid: Option[Long], apiKey: Option[String]) = DB.withConnection(db) { implicit c =>
    frontend.balance.on('uid -> uid, 'api_key -> apiKey)().map(row =>
      row[String]("currency") -> (row[BigDecimal]("amount"), row[BigDecimal]("hold"))
    ).toMap
  }

  def askBid(uid: Option[Long], apiKey: Option[String], base: String, counter: String, amount: BigDecimal, price: BigDecimal, isBid: Boolean) = DB.withConnection(db) { implicit c =>
    val res = frontend.orderNew.on(
      'uid -> uid,
      'api_key -> apiKey,
      'base -> base,
      'counter -> counter,
      'amount -> amount.bigDecimal,
      'price -> price.bigDecimal,
      'is_bid -> isBid
    )().map(_[Option[Boolean]]("order_new")).head
    if (res.isDefined) {
      flushMarketCaches(base, counter)
    }
    res.get
  }

  def ordersDepth(base: String, counter: String) = DB.withConnection(db) { implicit c =>
    play.api.cache.Cache.getOrElse("%s.%s.orders".format(base, counter)) {
      val PriceIndex = 0
      val AmountIndex = 1
      val (asks, bids, total_base, total_counter) = frontend.ordersDepth.on('base -> base, 'counter -> counter)().map(row => (
        row[Option[Array[Array[java.math.BigDecimal]]]]("asks").getOrElse(Array[Array[java.math.BigDecimal]]()),
        row[Option[Array[Array[java.math.BigDecimal]]]]("bids").getOrElse(Array[Array[java.math.BigDecimal]]()),
        row[java.math.BigDecimal]("total_base"),
        row[java.math.BigDecimal]("total_counter")
      )).head
      Json.obj(
        "asks" -> asks.map { a: Array[java.math.BigDecimal] =>
          Json.obj(
            "amount" -> a(AmountIndex).toPlainString,
            "price" -> a(PriceIndex).toPlainString
          )
        },
        "bids" -> bids.map { b: Array[java.math.BigDecimal] =>
          Json.obj(
            "amount" -> b(AmountIndex).toPlainString,
            "price" -> b(PriceIndex).toPlainString
          )
        },
        "total_base" -> total_base.toPlainString,
        "total_counter" -> total_counter.toPlainString
      )
    }
  }

  def userPendingTrades(uid: Option[Long], apiKey: Option[String], before: Option[DateTime] = None, limit: Option[Int] = None) = DB.withConnection(db) { implicit c =>
    frontend.userPendingTrades.on('uid -> uid, 'api_key -> apiKey, 'before -> before, 'limit -> limit)().map(row =>
      Trade(row[Long]("id"), if (row[Boolean]("is_bid")) "bid" else "ask", row[BigDecimal]("amount").bigDecimal.toPlainString, row[BigDecimal]("price").bigDecimal.toPlainString, row[String]("base"), row[String]("counter"), row[DateTime]("created"))
    ).toList
  }

  def cancel(uid: Option[Long], apiKey: Option[String], orderId: Long) = DB.withConnection(db) { implicit c =>
    // the database confirms for us that the user owns the transaction before cancelling it
    val res = frontend.orderCancel.on('uid -> uid, 'api_key -> apiKey, 'id -> orderId)().map(row =>
      (row[Option[String]]("base"), row[Option[String]]("counter"))
    ).head
    res match {
      case (Some(base: String), Some(counter: String)) =>
        flushMarketCaches(base, counter)
        true
      case _ =>
        false
    }
  }

  def withdraw(uid: Long, currency: String, amount: BigDecimal, address: String, tfa_code: Option[String]) = DB.withConnection(db) { implicit c =>
    val code = tfa_code.getOrElse("0") match {
      case "" => 0
      case c: String => c.toInt
    }
    frontend.withdrawCrypto.on(
      'uid -> uid,
      'currency -> currency,
      'amount -> amount.bigDecimal,
      'address -> address,
      'tfa_code -> code
    )().map(row => row[Option[Long]]("id")).head
  }

  def confirmWithdrawal(id: Long, token: String) = DB.withConnection(db) { implicit c =>
    frontend.confirmWithdrawal.on(
      'id -> id,
      'token -> token
    )().map(row => row[Boolean]("success")).head
  }

  def rejectWithdrawal(id: Long, token: String) = DB.withConnection(db) { implicit c =>
    frontend.rejectWithdrawal.on(
      'id -> id,
      'token -> token
    )().map(row => row[Boolean]("success")).head
  }

  def addresses(uid: Long, currency: String) = DB.withConnection(db) { implicit c =>
    frontend.getAddresses.on('uid -> uid, 'currency -> currency)().map(row => row[String]("o_address")).toList
  }

  def addresses(uid: Long) = DB.withConnection(db) { implicit c =>
    tuplesToGroupedMap(
      frontend.getAllAddresses.on('uid -> uid)().map(
        row => (row[String]("o_currency"), row[String]("o_address"))
      ).toList
    )
  }

  def tuplesToGroupedMap[K, L](list: List[(K, L)]) = {
    list.groupBy(_._1).map { (element: (K, List[(K, L)])) =>
      element._1 -> element._2.map(_._2)
    }
  }

  def pendingWithdrawals(uid: Long) = DB.withConnection(db) { implicit c =>
    tuplesToGroupedMap(
      frontend.getAllWithdrawals.on('uid -> uid)().map(row =>
        (
          row[String]("currency"),
          Withdrawal(
            row[Long]("id"),
            row[BigDecimal]("amount").bigDecimal.toPlainString,
            row[BigDecimal]("fee").bigDecimal.toPlainString,
            row[DateTime]("created"), row[String]("info"),
            row[String]("currency")
          )
        )
      ).toList
    )
  }

  def pendingDeposits(uid: Long) = DB.withConnection(db) { implicit c =>
    tuplesToGroupedMap(
      frontend.getAllDeposits.on('uid -> uid)().map(row =>
        (
          row[String]("currency"),
          Withdrawal(
            row[Long]("id"),
            row[BigDecimal]("amount").bigDecimal.toPlainString,
            row[BigDecimal]("fee").bigDecimal.toPlainString,
            row[DateTime]("created"),
            row[String]("info"),
            row[String]("currency")
          )
        )
      ).toList
    )
  }

  def recentTrades(base: String, counter: String) = DB.withConnection(db) { implicit c =>
    play.api.cache.Cache.getOrElse("%s.%s.trades".format(base, counter)) {
      Json.toJson(frontend.recentTrades.on(
        'base -> base,
        'counter -> counter
      )().map(row =>
          TradeHistory(row[BigDecimal]("amount").bigDecimal.toPlainString,
            BigDecimal(0).toString(), //the fee doesn't matter... TODO: don't include the fee here
            row[DateTime]("created"),
            row[BigDecimal]("price").bigDecimal.toPlainString,
            row[String]("base"),
            row[String]("counter"),
            if (row[Boolean]("is_bid")) "bid" else "ask")
        ).toList
      )
    }
  }

}
case class Withdrawal(id: Long, amount: String, fee: String, created: DateTime, info: String, currency: String)

object Withdrawal {
  implicit val writes = Json.writes[Withdrawal]
  implicit val format = Json.format[Withdrawal]
}

case class Trade(id: Long, typ: String, amount: String, price: String, base: String, counter: String, created: DateTime)

object Trade {
  implicit val writes = Json.writes[Trade]
  implicit val format = Json.format[Trade]
}

case class Match(amount: BigDecimal, price: BigDecimal, created: DateTime, base: String, counter: String)
object Match {
  def toJson(m: Match) = {
    Json.obj(
      "amount" -> m.amount.bigDecimal.toPlainString,
      "price" -> m.price.bigDecimal.toPlainString,
      "created" -> m.created.toString,
      "pair" -> "%s/%s".format(m.base, m.counter),
      "base" -> m.base,
      "counter" -> m.counter
    )
  }
}
