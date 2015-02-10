// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package models

import play.api.db.DB
import play.api.Play.current
import anorm._
import anorm.SqlParser._
import play.api.Play
import java.sql.Timestamp
import org.joda.time.DateTime
import securesocial.core.{ Token, SocialUser }
import service.sql.frontend
import service.{ PGP, TOTPSecret }
import play.api.libs.json.Json
import java.security.SecureRandom

case class TradeHistory(amount: String, fee: String, created: DateTime, price: String, base: String, counter: String, typ: String)

object TradeHistory {
  implicit val writes = Json.writes[TradeHistory]
  implicit val format = Json.format[TradeHistory]
}

case class DepositWithdrawHistory(amount: String, fee: String, created: DateTime, currency: String, typ: String, address: String)

object DepositWithdrawHistory {
  implicit val writes = Json.writes[DepositWithdrawHistory]
  implicit val format = Json.format[DepositWithdrawHistory]
}

class UserModel(val db: String = "default") {

  import globals.timestampColumn
  import globals.symbolColumn
  import globals.bigDecimalColumn

  def create(email: String, password: String, onMailingList: Boolean, pgp: Option[String], token: String) = DB.withConnection(db) { implicit c =>
    frontend.createUserComplete.on(
      'email -> email,
      'password -> password,
      'onMailingList -> onMailingList,
      'pgp -> pgp,
      'token -> token
    ).map(row => row[Option[Long]]("id")).list.head
  }

  // insecure version, usable only in tests
  def create(email: String, password: String, onMailingList: Boolean) = DB.withConnection(db) { implicit c =>
    frontend.createUserInsecure.on(
      'email -> email,
      'password -> password,
      'onMailingList -> onMailingList
    ).map(row => row[Long]("id")).list.headOption
  }

  def addFakeMoney(uid: Long, currency: String, amount: BigDecimal) = DB.withConnection(db) { implicit c =>
    if (Play.current.configuration.getBoolean("fakeexchange").get) {
      try {
        frontend.addFakeMoney.on(
          'uid -> uid,
          'currency -> currency,
          'amount -> amount.bigDecimal
        ).execute()
      } catch {
        case _: Throwable =>
          false
      }
    } else {
      false
    }
  }

  def subtractFakeMoney(uid: Long, currency: String, amount: BigDecimal) = DB.withConnection(db) { implicit c =>
    if (Play.current.configuration.getBoolean("fakeexchange").get) {
      try {
        frontend.removeFakeMoney.on(
          'uid -> uid,
          'currency -> currency,
          'amount -> amount.bigDecimal
        ).execute()
      } catch {
        case _: Throwable =>
          false
      }
    } else {
      false
    }
  }

  def findUserById(id: Long): Option[SocialUser] =
    DB.withConnection(db) { implicit c =>
      frontend.findUserById.on(
        'id -> id
      )().map(row =>
          new SocialUser(
            row[Long]("id"),
            row[String]("email"),
            row[Int]("verification"),
            row[Boolean]("on_mailing_list"),
            row[Boolean]("tfa_enabled"),
            row[Option[String]]("pgp")
          )
        ).headOption
    }

  def userExists(email: String): Boolean = DB.withConnection(db) { implicit c =>
    frontend.userExists.on(
      'email -> email
    )().map(row =>
        row[Boolean]("user_exists")
      ).head
  }

  def userHasTotp(email: String): Boolean = DB.withConnection(db) { implicit c =>
    frontend.userHasTotp.on(
      'email -> email
    )().map(row =>
        row[Option[Boolean]]("user_has_totp").getOrElse(false)
      ).head
  }

  def totpLoginStep1(email: String, password: String, browserHeaders: String, ip: String): Option[String] = DB.withConnection(db) { implicit c =>
    frontend.totpLoginStep1.on(
      'email -> email,
      'password -> password,
      'browser_headers -> browserHeaders,
      'ip -> ip
    )().map(row =>
        row[Option[String]]("totp_login_step1")
      ).head
  }

  def totpLoginStep2(email: String, totpHash: String, totpToken: String, browserHeaders: String, ip: String): Option[SocialUser] = DB.withConnection(db) { implicit c =>
    frontend.totpLoginStep2.on(
      'email -> email,
      'totp_hash -> totpHash,
      'totp_token -> safeToInt(totpToken),
      'browser_headers -> browserHeaders,
      'ip -> ip
    )().map(row => (row[Option[Long]]("id"),
        row[Option[String]]("email"),
        row[Option[Int]]("verification"),
        row[Option[Boolean]]("on_mailing_list"),
        row[Option[Boolean]]("tfa_enabled"),
        row[Option[String]]("pgp")) match {
          case (Some(id: Long),
            Some(email: String),
            Some(verification: Int),
            Some(on_mailing_list: Boolean),
            Some(tfa_enabled: Boolean),
            pgp: Option[String]) =>
            Some(SocialUser(id, email, verification, on_mailing_list, tfa_enabled, pgp))
          case _ =>
            None
        }
      ).head
  }

  def findUserByEmailAndPassword(email: String, password: String, browserHeaders: String, ip: String): Option[SocialUser] = DB.withConnection(db) { implicit c =>
    frontend.findUserByEmailAndPassword.on(
      'email -> email,
      'password -> password,
      'browser_headers -> browserHeaders,
      'ip -> ip
    )().map(row => (row[Option[Long]]("id"),
        row[Option[String]]("email"),
        row[Option[Int]]("verification"),
        row[Option[Boolean]]("on_mailing_list"),
        row[Option[Boolean]]("tfa_enabled"),
        row[Option[String]]("pgp")) match {
          case (Some(id: Long),
            Some(email: String),
            Some(verification: Int),
            Some(on_mailing_list: Boolean),
            Some(tfa_enabled: Boolean),
            pgp: Option[String]) =>
            Some(SocialUser(id, email, verification, on_mailing_list, tfa_enabled, pgp))
          case _ =>
            None
        }
      ).head
  }

  def tradeHistory(uid: Long) = DB.withConnection(db) { implicit c =>
    frontend.tradeHistory.on(
      'id -> uid
    )().map(row =>
        TradeHistory(row[BigDecimal]("amount").bigDecimal.toPlainString,
          row[BigDecimal]("fee").bigDecimal.toPlainString,
          row[DateTime]("created"),
          row[BigDecimal]("price").bigDecimal.toPlainString,
          row[String]("base"),
          row[String]("counter"),
          row[String]("type"))
      ).toList
  }

  def depositWithdrawHistory(uid: Long) = DB.withConnection(db) { implicit c =>
    frontend.depositWithdrawHistory.on(
      'id -> uid
    )().map(row =>
        DepositWithdrawHistory(
          row[BigDecimal]("amount").bigDecimal.toPlainString,
          row[BigDecimal]("fee").bigDecimal.toPlainString,
          row[DateTime]("created"),
          row[String]("currency"),
          row[String]("type"),
          row[Option[String]]("address").getOrElse(""))
      ).toList
  }

  /**
   * Finds a token
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation
   *
   * @param token the token id
   * @return
   */
  def findToken(token: String): Option[Token] = DB.withConnection(db) { implicit c =>
    frontend.findToken.on(
      'token -> token
    )().map(row =>
        Token(token, row[String]("email"), row[DateTime]("creation"), row[DateTime]("expiration"), row[Boolean]("is_signup"))
      ).headOption
  }

  /**
   * Deletes a token
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation
   *
   * @param uuid the token id
   */
  def deleteToken(uuid: String) = DB.withConnection(db) { implicit c =>
    frontend.deleteToken.on(
      'token -> uuid
    ).execute()
  }

  /**
   * Deletes all expired tokens
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation
   *
   */
  def deleteExpiredTokens() = DB.withConnection(db) { implicit c =>
    frontend.deleteExpiredTokens.execute()
  }

  def deleteExpiredTOTPBlacklistTokens() = DB.withConnection(db) { implicit c =>
    frontend.deleteExpiredTOTPBlacklistTokens.execute()
  }

  def saveUser(id: Long, email: String, onMailingList: Boolean) = DB.withConnection(db) { implicit c =>
    frontend.updateUser.on(
      'id -> id,
      'email -> email,
      'onMailingList -> onMailingList
    ).execute()
  }

  def userChangePass(id: Long, oldPassword: String, newPassword: String) = DB.withConnection(db) { implicit c =>
    frontend.userChangePassword.on(
      'user_id -> id,
      'old_password -> oldPassword,
      'new_password -> newPassword
    )().map(row =>
        row[Boolean]("user_change_password")
      ).head
  }

  def userResetPass(email: String, token: String, password: String) = DB.withConnection(db) { implicit c =>
    frontend.userResetPasswordComplete.on(
      'email -> email,
      'token -> token,
      'password -> password
    )().map(row =>
        row[Boolean]("success")
      ).head
  }

  def trustedActionStart(email: String, isSignup: Boolean) = DB.withConnection(db) { implicit c =>
    frontend.trustedActionStart.on(
      'email -> email,
      'is_signup -> isSignup
    )().map(row =>
        row[Boolean]("success")
      ).head
  }

  def genTFASecret(uid: Long) = DB.withConnection(db) { implicit c =>
    val secret = TOTPSecret()
    val random = new SecureRandom
    // build a string that will be parsed into an array in the postgres function
    // generate a 6 digit random numeber that doesn't start with a 0
    val otps = Seq.fill(10)((random.nextInt(9) + 1).toString concat "%05d".format(random.nextInt(100000)))
    // everything is off by default
    val success = frontend.updateTfaSecret.on(
      'id -> uid,
      'secret -> secret.toBase32,
      'otps -> otps.mkString(",")
    )().map(row =>
        row[Boolean]("success")
      ).head
    if (success) {
      Some(secret, otps)
    } else {
      None
    }
  }

  def turnOffTFA(uid: Long, tfa_code: String, password: String) = DB.withConnection(db) { implicit c =>
    frontend.turnoffTfa.on(
      'id -> uid,
      'tfa_code -> safeToInt(tfa_code),
      'password -> password
    )().map(row =>
        row[Boolean]("success")
      ).head
  }

  def addPGP(uid: Long, password: String, tfa_code: Option[String], pgp: String) = DB.withConnection(db) { implicit c =>
    frontend.userAddPgp.on(
      'id -> uid,
      'password -> password,
      'tfa_code -> optStrToInt(tfa_code),
      'pgp -> pgp
    )().map(row =>
        row[Boolean]("success")
      ).head
  }

  def removePGP(uid: Long, password: String, tfa_code: Option[String]) = DB.withConnection(db) { implicit c =>
    frontend.userRemovePgp.on(
      'id -> uid,
      'password -> password,
      'tfa_code -> optStrToInt(tfa_code)
    )().map(row =>
        row[Boolean]("success")
      ).head
  }

  def turnOnTFA(uid: Long, tfa_code: String, password: String) = DB.withConnection(db) { implicit c =>
    frontend.turnonTfa.on(
      'id -> uid,
      'tfa_code -> safeToInt(tfa_code),
      'password -> password
    )().map(row =>
        row[Boolean]("success")
      ).head
  }

  def turnOffEmails(uid: Long) = DB.withConnection(db) { implicit c =>
    frontend.turnoffEmails.on(
      'id -> uid
    )().map(row =>
        row[Boolean]("success")
      ).head
  }

  def turnOnEmails(uid: Long) = DB.withConnection(db) { implicit c =>
    frontend.turnonEmails.on(
      'id -> uid
    )().map(row =>
        row[Boolean]("success")
      ).head
  }

  def userPgpByEmail(email: String) = DB.withConnection(db) { implicit c =>
    frontend.userPgpByEmail.on(
      'email -> email
    )().map(row =>
        row[Option[String]]("pgp")
      ).head
  }

  private def optStrToInt(optStr: Option[String]) = {
    safeToInt(optStr.getOrElse(""))
  }

  private def safeToInt(str: String) = {
    try {
      str.toInt
    } catch {
      case _: Throwable => 0
    }
  }
}
