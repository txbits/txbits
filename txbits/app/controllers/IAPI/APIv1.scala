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

package controllers.IAPI

import javax.inject.Inject

import controllers.Util
import play.api._
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.i18n.MessagesApi
import service.{ PGP, TOTPUrl }
import org.postgresql.util.PSQLException
import org.apache.commons.codec.binary.Base64.encodeBase64
import java.security.SecureRandom
import play.api.i18n.{ Lang, MessagesApi, I18nSupport, Messages }

class APIv1 @Inject() (val messagesApi: MessagesApi) extends Controller with securesocial.core.SecureSocial with I18nSupport {
  // Json serializable case classes have implicit definitions in their companion objects

  import globals._

  implicit val rds_cancel = (__ \ 'order).read[Long]
  implicit val rds_uname = (__ \ 'username).read[String]

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
    val balances = globals.engineModel.balance(Some(request.user.id), None)
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
              val res = globals.engineModel.askBid(Some(request.user.id), None, base, counter, amount, price, isBid = false)
              if (res.isDefined) {
                Ok(Json.toJson(res.get))
              } else {
                BadRequest(Json.obj("message" -> Messages("messages.api.error.nonsufficientfunds")))
              }
            case Some((active, minAmount)) if active =>
              BadRequest(Json.obj("message" -> Messages("messages.api.error.amountmustbeatleast").format(minAmount)))
            case Some((active, minAmount)) =>
              BadRequest(Json.obj("message" -> Messages("messages.api.error.tradingsuspendedon").format(base, counter)))
            case _ =>
              BadRequest(Json.obj("message" -> Messages("messages.api.error.invalidpair")))
          }
        } else {
          BadRequest(Json.obj("message" -> Messages("messages.api.error.thepriceandamountmustbepositive")))
        }
      }).getOrElse(
        BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoparseinput")))
      )
    } catch {
      case _: Throwable =>
        BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoplaceask")))
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
              val res = globals.engineModel.askBid(Some(request.user.id), None, base, counter, amount, price, isBid = true)
              if (res.isDefined) {
                Ok(Json.toJson(res.get))
              } else {
                BadRequest(Json.obj("message" -> Messages("messages.api.error.nonsufficientfunds")))
              }
            case Some((active, minAmount)) if active =>
              BadRequest(Json.obj("message" -> Messages("messages.api.error.amountmustbeatleast").format(minAmount)))
            case Some((active, minAmount)) =>
              BadRequest(Json.obj("message" -> Messages("messages.api.error.tradingsuspendedon").format(base, counter)))
            case _ =>
              BadRequest(Json.obj("message" -> Messages("messages.api.error.invalidpair")))
          }
        } else {
          BadRequest(Json.obj("message" -> Messages("messages.api.error.thepriceandamountmustbepositive")))
        }
      }).getOrElse(
        BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoparseinput")))
      )
    } catch {
      case _: Throwable =>
        BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoplacebid")))
    }
  }

  def changeUsername = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    request.request.body.validate(rds_uname).map {
      case (username) =>
        val res = globals.userModel.saveUser(request.user.id, request.user.email, request.user.onMailingList, username)
        if (res) {
          Ok(Json.obj())
        } else {
          BadRequest(Json.obj("message" -> Messages("messages.api.error.invalidusername")))
        }
    }.getOrElse(
      BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoparseinput")))
    )
  }

  def cancel = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    request.request.body.validate(rds_cancel).map {
      case (order) =>
        val res = globals.engineModel.cancel(Some(request.user.id), None, order)
        if (res) {
          Ok(Json.obj())
        } else {
          BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtocancelorder")))
        }
    }.getOrElse(
      BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoparseinput")))
    )
  }

  def depositWithdrawHistory = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    val (before, limit, lastId) = Util.parse_pagination_params
    Ok(Json.toJson(userModel.depositWithdrawHistory(request.user.id, before, limit, lastId)))
  }

  def tradeHistory = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    val (before, limit, lastId) = Util.parse_pagination_params
    Ok(Json.toJson(userModel.tradeHistory(Some(request.user.id), None, before, limit, lastId)))
  }

  def loginHistory = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    val (before, limit, lastId) = Util.parse_pagination_params
    Ok(Json.toJson(globals.logModel.getLoginEvents(request.user.id, before, limit, lastId)))
  }

  def pendingTrades = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    val orders = globals.engineModel.userPendingTrades(Some(request.user.id), None)
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
    val password = (request.request.body \ "password").validate[String].get

    if (globals.userModel.turnOffTFA(request.user.id, tfa_code, password)) {
      Ok(Json.obj())
    } else {
      BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoturnofftwofactorauth")))
    }
  }

  def turnOnEmails() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    if (globals.userModel.turnOnEmails(request.user.id)) {
      Ok(Json.obj())
    } else {
      BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoaddtomailinglist")))
    }
  }

  def turnOffEmails() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    if (globals.userModel.turnOffEmails(request.user.id)) {
      Ok(Json.obj())
    } else {
      BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoremovefrommailinglist")))
    }
  }

  // This creates a secret, stores it and shows it to the user
  // Then the user has to verify that they know the secret by providing the code from it
  // and then they can turn on 2fa for login / withdrawal
  def genTOTPSecret() = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    if (!request.user.TFAEnabled) {
      val secret = globals.userModel.genTFASecret(request.user.id)
      if (secret.isEmpty) {
        BadRequest(Json.obj("message" -> Messages("messages.api.error.twofactorauthenticationisalready")))
      } else {
        Ok(Json.obj("secret" -> secret.get._1.toBase32, "otps" -> secret.get._2, "otpauth" -> TOTPUrl.totpSecretToUrl(request.user.email, secret.get._1)))
      }
    } else {
      BadRequest(Json.obj("message" -> Messages("messages.api.error.twofactorauthenticationisalready")))
    }
  }

  def turnOnTFA() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    val tfa_code = (request.request.body \ "tfa_code").validate[String].get
    val password = (request.request.body \ "password").validate[String].get

    if (globals.userModel.turnOnTFA(request.user.id, tfa_code, password)) {
      Ok(Json.obj())
    } else {
      BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoturnontwofactorauth")))
    }
  }

  def addPgp() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    (for (
      password <- (request.request.body \ "password").validate[String];
      pgp <- (request.request.body \ "pgp").validate[String];
      parsedKey = PGP.parsePublicKey(pgp);
      tfa_code = (request.request.body \ "tfa_code").asOpt[String]
    ) yield {
      if (parsedKey.isDefined) {
        if (globals.userModel.addPGP(request.user.id, password, tfa_code, parsedKey.get._2)) {
          Ok(Json.obj())
        } else {
          BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoaddpgpkey")))
        }
      } else {
        BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoaddpgpkeynovalid")))
      }
    }).getOrElse(
      BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoparseinput")))
    )
  }

  def removePgp() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    val tfa_code = (request.request.body \ "tfa_code").asOpt[String]
    val password = (request.request.body \ "password").validate[String].get
    if (globals.userModel.removePGP(request.user.id, password, tfa_code)) {
      Ok(Json.obj())
    } else {
      BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoremovepgpkey")))
    }
  }

  def addApiKey() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    val random = new SecureRandom
    val bytes = new Array[Byte](18)
    random.nextBytes(bytes)
    val apiKey = new String(encodeBase64(bytes))
    globals.userModel.addApiKey(request.user.id, apiKey)
    Ok(Json.obj())
  }

  def updateApiKey() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    (for (
      apiKey <- (request.request.body \ "api_key").validate[String];
      trading <- (request.request.body \ "trading").validate[Boolean];
      tradeHistory <- (request.request.body \ "trade_history").validate[Boolean];
      listBalance <- (request.request.body \ "list_balance").validate[Boolean];
      comment <- (request.request.body \ "comment").validate[String];
      tfa_code = (request.request.body \ "tfa_code").asOpt[String]
    ) yield {
      if (globals.userModel.updateApiKey(request.user.id, tfa_code, apiKey, comment.take(32), trading, tradeHistory, listBalance)) {
        Ok(Json.obj())
      } else {
        BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoupdateapikey")))
      }
    }).getOrElse(
      BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoparseinput")))
    )
  }

  def disableApiKey() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    (for (
      apiKey <- (request.request.body \ "api_key").validate[String];
      tfa_code = (request.request.body \ "tfa_code").asOpt[String]
    ) yield {
      if (globals.userModel.disableApiKey(request.user.id, tfa_code, apiKey)) {
        Ok(Json.obj())
      } else {
        BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtodisableapikey")))
      }
    }).getOrElse(
      BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoparseinput")))
    )
  }

  def getApiKeys = SecuredAction(ajaxCall = true)(parse.anyContent) { implicit request =>
    Ok(Json.toJson(userModel.getApiKeys(request.user.id)))
  }

  def withdraw() = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    val body = request.request.body
    (for (
      currency <- (body \ "currency").validate[String];
      amount <- (body \ "amount").validate[BigDecimal];
      address <- (body \ "address").validate[String];
      tfa_code = (body \ "tfa_code").asOpt[String]
    ) yield {

      if (CryptoAddress.isValid(address, currency, Play.current.configuration.getBoolean("fakeexchange").get)) {
        try {
          val res = globals.engineModel.withdraw(request.user.id, currency, amount, address, tfa_code)
          if (res.isDefined) {
            if (res.get != -1) {
              Ok(Json.obj())
            } else {
              BadRequest(Json.obj("message" -> Messages("messages.api.error.wrongtwofactorauthcode")))
            }
          } else {
            BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtowithdraw")))
          }
        } catch {
          case e: PSQLException => {
            BadRequest(Json.obj("message" -> (e.getServerErrorMessage.getMessage match {
              case "new row for relation \"balances\" violates check constraint \"no_hold_above_balance\"" => Messages("messages.api.error.nonsufficientfunds")
              case _: String => {
                Logger.error(e.toString + e.getStackTrace)
                e.getServerErrorMessage.getMessage
              }
            })))
          }
        }
      } else {
        BadRequest(Json.obj("message" -> Messages("messages.api.error.invalidaddress")))
      }
    }).getOrElse(
      BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoparseinput")))
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
