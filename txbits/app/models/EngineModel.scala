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
import service.SQLText

class EngineModel(val db: String = "default") {

  import globals.bigDecimalColumn
  import globals.timestampColumn

  def clean() = DB.withConnection(db)(implicit c =>
    SQLText.cleanForTest.execute()
  )

  def testSetup() = DB.withConnection(db)(implicit c =>
    SQLText.setupForTest.execute()
  )

  def setFees(currency: String, method: String, depositConstant: BigDecimal, depositLinear: BigDecimal, withdrawConstant: BigDecimal, withdrawLinear: BigDecimal) = DB.withConnection(db)(implicit c =>
    SQLText.setFees.on(
      'currency -> currency,
      'method -> method,
      'depositConstant -> depositConstant.bigDecimal,
      'depositLinear -> depositLinear.bigDecimal,
      'withdrawConstant -> withdrawConstant.bigDecimal,
      'withdrawLinear -> withdrawLinear.bigDecimal
    ).execute()
  )

  def balance(uid: Long) = DB.withConnection(db) { implicit c =>
    SQLText.balance.on('uid -> uid)().map(row =>
      row[String]("currency") -> (row[BigDecimal]("amount"), row[BigDecimal]("hold"))
    ).toMap
  }

  def bid(uid: Long, base: String, counter: String, amount: BigDecimal, price: BigDecimal) = DB.withConnection(db) { implicit c =>
    SQLText.askBid.on(
      'uid -> uid,
      'base -> base,
      'counter -> counter,
      'amount -> amount.bigDecimal,
      'price -> price.bigDecimal,
      'type -> "bid"
    ).executeInsert[Option[Long]]()
  }

  def ask(uid: Long, base: String, counter: String, amount: BigDecimal, price: BigDecimal) = DB.withConnection(db) { implicit c =>
    SQLText.askBid.on(
      'uid -> uid,
      'base -> base,
      'counter -> counter,
      'amount -> amount.bigDecimal,
      'price -> price.bigDecimal,
      'type -> "ask"
    ).executeInsert[Option[Long]]()
  }

  def userPendingTrades(uid: Long) = DB.withConnection(db) { implicit c =>
    SQLText.userPendingTrades.on('uid -> uid)().map(row =>
      Trade(row[Long]("id"), row[String]("type"), row[BigDecimal]("amount").bigDecimal.toPlainString, row[BigDecimal]("price").bigDecimal.toPlainString, row[String]("base"), row[String]("counter"), row[DateTime]("created"))
    ).toList
  }

  def openAsks(base: String, counter: String) = DB.withConnection(db) { implicit c =>
    SQLText.openAsks.on('base -> base, 'counter -> counter)().map(row =>
      OpenOrder(row[String]("type"), row[BigDecimal]("amount").bigDecimal.toPlainString, row[BigDecimal]("price").bigDecimal.toPlainString, row[String]("base"), row[String]("counter"))
    ).toList
  }

  def openBids(base: String, counter: String) = DB.withConnection(db) { implicit c =>
    SQLText.openBids.on('base -> base, 'counter -> counter)().map(row =>
      OpenOrder(row[String]("type"), row[BigDecimal]("amount").bigDecimal.toPlainString, row[BigDecimal]("price").bigDecimal.toPlainString, row[String]("base"), row[String]("counter"))
    ).toList
  }

  def cancel(uid: Long, orderId: Long) = DB.withConnection(db) { implicit c =>
    // the database confirms for us that the user owns the transaction before cancelling it
    SQLText.cancelTrade.on('uid -> uid, 'id -> orderId)().map(row => row[Boolean]("order_cancel")).head
  }

  def withdraw(uid: Long, currency: String, amount: BigDecimal, address: String) = DB.withConnection(db) { implicit c =>
    SQLText.withdrawCrypto.on(
      'uid -> uid,
      'currency -> currency,
      'amount -> amount.bigDecimal,
      'address -> address).executeInsert[Option[Long]]()
  }

  def addresses(uid: Long, currency: String) = DB.withConnection(db) { implicit c =>
    SQLText.getAddresses.on('uid -> uid, 'currency -> currency)().map(row => row[String]("address")).toList
  }

  def addresses(uid: Long) = DB.withConnection(db) { implicit c =>
    tuplesToGroupedMap(
      SQLText.getAllAddresses.on('uid -> uid)().map(
        row => (row[String]("currency"), row[String]("address"))
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
      SQLText.getAllWithdrawals.on('uid -> uid)().map(row =>
        (row[String]("currency"), Withdrawal(row[BigDecimal]("amount").bigDecimal.toPlainString, row[BigDecimal]("fee").bigDecimal.toPlainString, row[DateTime]("created"), row[String]("info")))
      ).toList
    )
  }

  def pendingDeposits(uid: Long) = DB.withConnection(db) { implicit c =>
    tuplesToGroupedMap(
      SQLText.getAllDeposits.on('uid -> uid)().map(row =>
        (row[String]("currency"), Withdrawal(row[BigDecimal]("amount").bigDecimal.toPlainString, row[BigDecimal]("fee").bigDecimal.toPlainString, row[DateTime]("created"), row[String]("info")))
      ).toList
    )
  }

  def recentTrades(base: String, counter: String) = DB.withConnection(db) { implicit c =>
    SQLText.recentTrades.on(
      'base -> base,
      'counter -> counter
    )().map(row =>
        TradeHistory(row[BigDecimal]("amount").bigDecimal.toPlainString,
          BigDecimal(0).toString(), //the fee doesn't matter... TODO: don't include the fee here
          row[DateTime]("created"),
          row[BigDecimal]("price").bigDecimal.toPlainString,
          row[String]("base"),
          row[String]("counter"),
          row[String]("type"))
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
      "created" -> m.created.toString(),
      "pair" -> "%s/%s".format(m.base, m.counter),
      "base" -> m.base,
      "counter" -> m.counter
    )
  }
}
