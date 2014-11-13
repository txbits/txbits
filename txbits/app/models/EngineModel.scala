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

  import globals.bigDecimalColumn
  import globals.timestampColumn
  import globals.rowToBigDecimalArrayArray

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

  def balance(uid: Long) = DB.withConnection(db) { implicit c =>
    frontend.balance.on('uid -> uid)().map(row =>
      row[String]("currency") -> (row[BigDecimal]("amount"), row[BigDecimal]("hold"))
    ).toMap
  }

  def askBid(uid: Long, base: String, counter: String, amount: BigDecimal, price: BigDecimal, isBid: Boolean) = DB.withConnection(db) { implicit c =>
    frontend.orderNew.on(
      'uid -> uid,
      'base -> base,
      'counter -> counter,
      'amount -> amount.bigDecimal,
      'price -> price.bigDecimal,
      'is_bid -> isBid
    )().map(_[Boolean]("order_new")).head
  }

  def ordersDepth(base: String, counter: String) = DB.withConnection(db) { implicit c =>
    frontend.ordersDepth.on('base -> base, 'counter -> counter)().map(row => (
      row[Option[Array[Array[java.math.BigDecimal]]]]("asks").getOrElse(Array[Array[java.math.BigDecimal]]()),
      row[Option[Array[Array[java.math.BigDecimal]]]]("bids").getOrElse(Array[Array[java.math.BigDecimal]]())
    )).head
  }

  def userPendingTrades(uid: Long) = DB.withConnection(db) { implicit c =>
    frontend.userPendingTrades.on('uid -> uid)().map(row =>
      Trade(row[Long]("id"), if (row[Boolean]("is_bid")) "bid" else "ask", row[BigDecimal]("amount").bigDecimal.toPlainString, row[BigDecimal]("price").bigDecimal.toPlainString, row[String]("base"), row[String]("counter"), row[DateTime]("created"))
    ).toList
  }

  def openAsks(base: String, counter: String) = DB.withConnection(db) { implicit c =>
    frontend.openAsks.on('base -> base, 'counter -> counter)().map(row =>
      OpenOrder("ask", row[BigDecimal]("amount").bigDecimal.toPlainString, row[BigDecimal]("price").bigDecimal.toPlainString, row[String]("base"), row[String]("counter"))
    ).toList
  }

  def openBids(base: String, counter: String) = DB.withConnection(db) { implicit c =>
    frontend.openBids.on('base -> base, 'counter -> counter)().map(row =>
      OpenOrder("bid", row[BigDecimal]("amount").bigDecimal.toPlainString, row[BigDecimal]("price").bigDecimal.toPlainString, row[String]("base"), row[String]("counter"))
    ).toList
  }

  def cancel(uid: Long, orderId: Long) = DB.withConnection(db) { implicit c =>
    // the database confirms for us that the user owns the transaction before cancelling it
    frontend.orderCancel.on('uid -> uid, 'id -> orderId)().map(row => row[Boolean]("order_cancel")).head
  }

  def withdraw(uid: Long, currency: String, amount: BigDecimal, address: String) = DB.withConnection(db) { implicit c =>
    frontend.withdrawCrypto.on(
      'uid -> uid,
      'currency -> currency,
      'amount -> amount.bigDecimal,
      'address -> address).map(row => row[Long]("o_id")).list.headOption
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
        (row[String]("currency"), Withdrawal(row[BigDecimal]("amount").bigDecimal.toPlainString, row[BigDecimal]("fee").bigDecimal.toPlainString, row[DateTime]("created"), row[String]("info")))
      ).toList
    )
  }

  def pendingDeposits(uid: Long) = DB.withConnection(db) { implicit c =>
    tuplesToGroupedMap(
      frontend.getAllDeposits.on('uid -> uid)().map(row =>
        (row[String]("currency"), Withdrawal(row[BigDecimal]("amount").bigDecimal.toPlainString, row[BigDecimal]("fee").bigDecimal.toPlainString, row[DateTime]("created"), row[String]("info")))
      ).toList
    )
  }

  def recentTrades(base: String, counter: String) = DB.withConnection(db) { implicit c =>
    frontend.recentTrades.on(
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
  }

}
case class Withdrawal(amount: String, fee: String, created: DateTime, info: String)

object Withdrawal {
  implicit val writes = Json.writes[Withdrawal]
  implicit val format = Json.format[Withdrawal]
}

case class Trade(id: Long, typ: String, amount: String, price: String, base: String, counter: String, created: DateTime)

object Trade {
  implicit val writes = Json.writes[Trade]
  implicit val format = Json.format[Trade]
}

case class OpenOrder(typ: String, amount: String, price: String, base: String, counter: String)

object OpenOrder {
  implicit val writes = Json.writes[OpenOrder]
  implicit val format = Json.format[OpenOrder]
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
