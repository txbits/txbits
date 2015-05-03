// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package controllers.API

import play.api._
import play.api.mvc._
import play.api.libs.json._

object APIv1 extends Controller {

  def balance = Action(parse.json) { implicit request =>
    val body = request.body
    (for (
      apiKey <- (body \ "api_key").validate[String]
    ) yield {
      val balances = globals.engineModel.apiBalance(apiKey)
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
              val res = globals.engineModel.apiAskBid(apiKey, base, counter, amount, price, isBid = false)
              if (res) {
                Ok(Json.obj())
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
              val res = globals.engineModel.apiAskBid(apiKey, base, counter, amount, price, isBid = true)
              if (res) {
                Ok(Json.obj())
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
      val res = globals.engineModel.apiCancel(apiKey, order)
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
      apiKey <- (body \ "api_key").validate[String]
    ) yield {
      Ok(Json.toJson(globals.userModel.apiTradeHistory(apiKey)))
    }).getOrElse(
      BadRequest(Json.obj("message" -> "Failed to parse input."))
    )
  }

  def pendingTrades = Action(parse.json) { implicit request =>
    val body = request.body
    (for (
      apiKey <- (body \ "api_key").validate[String]
    ) yield {
      val orders = globals.engineModel.apiUserPendingTrades(apiKey)
      Ok(Json.toJson(orders))
    }).getOrElse(
      BadRequest(Json.obj("message" -> "Failed to parse input."))
    )
  }
}
