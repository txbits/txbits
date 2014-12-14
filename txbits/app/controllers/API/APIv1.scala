// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package controllers.API

import play.api._
import play.api.mvc._
import play.api.libs.json._
import service.TOTPUrl
import org.postgresql.util.PSQLException

object APIv1 extends Controller with securesocial.core.SecureSocial {

  // Json serializable case classes have implicit definitions in their companion objects

  import globals._

  implicit val rds_cancel = (__ \ 'order).read[Long]

  def index = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    Ok(Json.obj())
  }

  def pairs = Action { implicit request =>
    Ok(Json.toJson(globals.metaModel.allPairsJson))
  }

  def currencies = Action { implicit request =>
    Ok(Json.toJson(globals.metaModel.currencies))
  }

  def tradeFees = Action { implicit request =>
    Ok(Json.toJson(globals.metaModel.tradeFees))
  }

  def dwFees = Action { implicit request =>
    Ok(Json.toJson(globals.metaModel.dwFees))
  }

  def dwLimits = Action { implicit request =>
    Ok(Json.toJson(globals.metaModel.dwLimits))
  }

  def requiredConfirms = Action { implicit request =>
    Ok(Json.toJson(globals.metaModel.getRequiredConfirmations))
  }

  def balance = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    val balances = globals.engineModel.balance(request.user.id)
    Ok(Json.toJson(balances.map({ c =>
      Json.obj(
        "currency" -> c._1,
        "amount" -> c._2._1.bigDecimal.toPlainString,
        "hold" -> c._2._2.bigDecimal.toPlainString
      )
    })
    ))
  }

  def ask = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    try {
      val body = request.request.body
      (for (
        base <- (body \ "base").validate[String];
        counter <- (body \ "counter").validate[String];
        amount <- (body \ "amount").validate[BigDecimal];
        price <- (body \ "price").validate[BigDecimal]
      ) yield {
        if (price > 0 && amount > 0) {
          globals.metaModel.activeMarkets.get(base, counter) match {
            case Some((active, minAmount)) if active && amount >= minAmount =>
              val res = globals.engineModel.askBid(request.user.id, base, counter, amount, price, isBid = false)
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

  def bid = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    try {
      val body = request.request.body
      (for (
        base <- (body \ "base").validate[String];
        counter <- (body \ "counter").validate[String];
        amount <- (body \ "amount").validate[BigDecimal];
        price <- (body \ "price").validate[BigDecimal]
      ) yield {
        if (price > 0 && amount > 0) {
          globals.metaModel.activeMarkets.get(base, counter) match {
            case Some((active, minAmount)) if active && amount >= minAmount =>
              val res = globals.engineModel.askBid(request.user.id, base, counter, amount, price, isBid = true)
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

  def cancel = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    request.request.body.validate(rds_cancel).map {
      case (order) =>
        val res = globals.engineModel.cancel(request.user.id, order)
        if (res) {
          Ok(Json.obj())
        } else {
          BadRequest(Json.obj("message" -> "Failed to cancel order."))
        }
    }.getOrElse(
      BadRequest(Json.obj("message" -> "Failed to parse input."))
    )
  }

  def openTrades(base: String, counter: String) = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    val PriceIndex = 0
    val AmountIndex = 1
    // a specific pair will be given as an argument
    val (asks, bids) = globals.engineModel.ordersDepth(base, counter)

    Ok(Json.obj(
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
      }
    )
    )
  }

  def recentTrades(base: String, counter: String) = Action { implicit request =>
    // a specific pair will be given as an argument
    Ok(Json.toJson(engineModel.recentTrades(base, counter)))
  }

  def depositWithdrawHistory = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    Ok(Json.toJson(userModel.depositWithdrawHistory(request.user.id)))
  }

  def tradeHistory = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    Ok(Json.toJson(userModel.tradeHistory(request.user.id)))
  }

  def loginHistory = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    Ok(Json.toJson(globals.logModel.getLoginEvents(request.user.id)))
  }

  def pendingTrades = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    val orders = globals.engineModel.userPendingTrades(request.user.id)
    Ok(Json.toJson(orders))
  }

  def depositCrypto(currency: String) = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    val res = globals.engineModel.addresses(request.user.id, currency)
    if (res.isEmpty) {
      Ok(Json.arr())
    } else {
      Ok(Json.toJson(res))
    }
  }

  def depositCryptoAll = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    val res = globals.engineModel.addresses(request.user.id)
    if (res.isEmpty) {
      Ok(Json.obj())
    } else {
      Ok(Json.toJson(res))
    }
  }

  def user = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    Ok(Json.toJson(request.user))
    //Do not display the tfasecret / qr code from this api call. The QR code needs to be protected better
    //.asInstanceOf[JsObject].+("qr", JsString(QrCodeGen.userTotpQrCode(request.user).getOrElse("")))))
  }

  def turnOffTFA() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    val tfa_code = (request.request.body \ "tfa_code").validate[String].get

    if (globals.userModel.turnOffTFA(request.user.id, tfa_code)) {
      Ok(Json.obj())
    } else {
      BadRequest(Json.obj("message" -> "Failed to turn off two factor auth."))
    }
  }

  def turnOnEmails() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    if (globals.userModel.turnOnEmails(request.user.id)) {
      Ok(Json.obj())
    } else {
      BadRequest(Json.obj("message" -> "Failed to add to mailing list."))
    }
  }

  def turnOffEmails() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    if (globals.userModel.turnOffEmails(request.user.id)) {
      Ok(Json.obj())
    } else {
      BadRequest(Json.obj("message" -> "Failed to remove from mailing list."))
    }
  }

  // This creates a secret, stores it and shows it to the user
  // Then the user has to verify that they know the secret by providing the code from it
  // and then they can turn on 2fa for login / withdrawal
  def genTOTPSecret() = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    if (!request.user.TFAEnabled) {
      val secret = globals.userModel.genTFASecret(request.user.id)
      if (secret.isEmpty) {
        BadRequest(Json.obj("message" -> "Two factor authentication is already enabled."))
      } else {
        Ok(Json.obj("secret" -> secret.get._1.toBase32, "otps" -> secret.get._2, "otpauth" -> TOTPUrl.totpSecretToUrl(request.user.email, secret.get._1)))
      }
    } else {
      BadRequest(Json.obj("message" -> "Two factor authentication is already enabled."))
    }
  }

  def turnOnTFA() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    val tfa_code = (request.request.body \ "tfa_code").validate[String].get
    if (globals.userModel.turnOnTFA(request.user.id, tfa_code)) {
      Ok(Json.obj())
    } else {
      BadRequest(Json.obj("message" -> "Failed to turn on two factor auth."))
    }
  }

  def withdraw = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    val body = request.request.body
    (for (
      currency <- (body \ "currency").validate[String];
      amount <- (body \ "amount").validate[BigDecimal];
      address <- (body \ "address").validate[String]
    ) yield {
      val tfa_code = (body \ "tfa_code").asOpt[String]

      if (CryptoAddress.isValid(address, currency, Play.current.configuration.getBoolean("fakeexchange").get)) {
        try {
          val res = globals.engineModel.withdraw(request.user.id, currency, amount, address, tfa_code)
          if (res.isDefined) {
            Ok(Json.obj())
          } else {
            BadRequest(Json.obj("message" -> "Failed to withdraw"))
          }
        } catch {
          case e: PSQLException => {
            BadRequest(Json.obj("message" -> (e.getServerErrorMessage.getMessage match {
              case "new row for relation \"balances\" violates check constraint \"no_hold_above_balance\"" => "Non-sufficient funds."
              case _: String => {
                Logger.error(e.toString + e.getStackTrace)
                e.getServerErrorMessage.getMessage
              }
            })))
          }
        }
      } else {
        BadRequest(Json.obj("message" -> "Invalid address"))
      }
    }).getOrElse(
      BadRequest(Json.obj("message" -> "Failed to parse input."))
    )
  }

  def pendingWithdrawalsAll = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    val res = globals.engineModel.pendingWithdrawals(request.user.id)
    Ok(Json.toJson(res))
  }

  def pendingDepositsAll = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    val res = globals.engineModel.pendingDeposits(request.user.id)
    Ok(Json.toJson(res))
  }

}