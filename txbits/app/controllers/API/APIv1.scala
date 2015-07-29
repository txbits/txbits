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

package controllers.API

import javax.inject.Inject

import play.api._
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import org.joda.time.DateTime
import play.api.i18n.MessagesApi

class APIv1 @Inject() (val messagesApi: MessagesApi) extends Controller with I18nSupport {

  def balance = Action(parse.json) { implicit request =>
    val body = request.body
    (for (
      apiKey <- (body \ "api_key").validate[String]
    ) yield {
      val balances = globals.engineModel.balance(None, Some(apiKey))
      Ok(Json.toJson(balances.map({ c =>
        Json.obj(
          "currency" -> c._1,
          "amount" -> c._2._1.bigDecimal.toPlainString,
          "hold" -> c._2._2.bigDecimal.toPlainString
        )
      })
      ))
    }).getOrElse(
      BadRequest(Json.obj("message" -> "Failed to parse input."))
    )
  }

  def ask = Action(parse.json) { implicit request =>
    try {
      val body = request.body
      (for (
        apiKey <- (body \ "api_key").validate[String];
        base <- (body \ "base").validate[String];
        counter <- (body \ "counter").validate[String];
        amount <- (body \ "amount").validate[BigDecimal];
        price <- (body \ "price").validate[BigDecimal]
      ) yield {
        if (price > 0 && amount > 0) {
          globals.metaModel.activeMarkets.get(base, counter) match {
            case Some((active, minAmount)) if active && amount >= minAmount =>
              val res = globals.engineModel.askBid(None, Some(apiKey), base, counter, amount, price, isBid = false)
              if (res.isDefined) {
                Ok(res.get)
              } else {
                BadRequest(Json.obj("message" -> "Non-sufficient funds."))
              }
            case Some((active, minAmount)) if active =>
              BadRequest(Json.obj("message" -> "Amount must be at least %s.".format(minAmount)))
            case Some((active, minAmount)) =>
              BadRequest(Json.obj("message" -> "Trading suspended on %s/%s.".format(base, counter)))
            case _ =>
              BadRequest(Json.obj("message" -> "Invalid pair."))
          }
        } else {
          BadRequest(Json.obj("message" -> "The price and amount must be positive."))
        }
      }).getOrElse(
        BadRequest(Json.obj("message" -> "Failed to parse input."))
      )
    } catch {
      case _: Throwable =>
        BadRequest(Json.obj("message" -> "Failed to place ask."))
    }
  }

  def bid = Action(parse.json) { implicit request =>
    try {
      val body = request.body
      (for (
        apiKey <- (body \ "api_key").validate[String];
        base <- (body \ "base").validate[String];
        counter <- (body \ "counter").validate[String];
        amount <- (body \ "amount").validate[BigDecimal];
        price <- (body \ "price").validate[BigDecimal]
      ) yield {
        if (price > 0 && amount > 0) {
          globals.metaModel.activeMarkets.get(base, counter) match {
            case Some((active, minAmount)) if active && amount >= minAmount =>
              val res = globals.engineModel.askBid(None, Some(apiKey), base, counter, amount, price, isBid = true)
              if (res.isDefined) {
                Ok(res.get)
              } else {
                BadRequest(Json.obj("message" -> "Non-sufficient funds."))
              }
            case Some((active, minAmount)) if active =>
              BadRequest(Json.obj("message" -> "Amount must be at least %s.".format(minAmount)))
            case Some((active, minAmount)) =>
              BadRequest(Json.obj("message" -> "Trading suspended on %s/%s.".format(base, counter)))
            case _ =>
              BadRequest(Json.obj("message" -> "Invalid pair."))
          }
        } else {
          BadRequest(Json.obj("message" -> "The price and amount must be positive."))
        }
      }).getOrElse(
        BadRequest(Json.obj("message" -> "Failed to parse input."))
      )
    } catch {
      case _: Throwable =>
        BadRequest(Json.obj("message" -> "Failed to place bid."))
    }
  }

  def cancel = Action(parse.json) { implicit request =>
    val body = request.body
    (for (
      apiKey <- (body \ "api_key").validate[String];
      order <- (body \ "order").validate[Long]
    ) yield {
      val res = globals.engineModel.cancel(None, Some(apiKey), order)
      if (res) {
        Ok(Json.obj())
      } else {
        BadRequest(Json.obj("message" -> "Failed to cancel order."))
      }
    }).getOrElse(
      BadRequest(Json.obj("message" -> "Failed to parse input."))
    )
  }

  def tradeHistory = Action(parse.json) { implicit request =>
    val body = request.body
    (for (
      apiKey <- (body \ "api_key").validate[String];
      before = (body \ "before").asOpt[DateTime];
      limit = (body \ "limit").asOpt[Int];
      lastId = (body \ "last_id").asOpt[Long]
    ) yield {
      Ok(Json.toJson(globals.userModel.tradeHistory(None, Some(apiKey), before, limit, lastId)))
    }).getOrElse(
      BadRequest(Json.obj("message" -> "Failed to parse input."))
    )
  }

  def pendingTrades = Action(parse.json) { implicit request =>
    val body = request.body
    (for (
      apiKey <- (body \ "api_key").validate[String]
    ) yield {
      val orders = globals.engineModel.userPendingTrades(None, Some(apiKey))
      Ok(Json.toJson(orders))
    }).getOrElse(
      BadRequest(Json.obj("message" -> "Failed to parse input."))
    )
  }
}
