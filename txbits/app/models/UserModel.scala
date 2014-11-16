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
import service.TOTPSecret
import play.api.libs.json.Json

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

  def create(email: String, password: String, onMailingList: Boolean, token: String) = DB.withConnection(db) { implicit c =>
    frontend.createUserComplete.on(
      'email -> email,
      'password -> password,
      'onMailingList -> onMailingList,
      'token -> token
    ).map(row => row[Long]("id")).list.headOption
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
            row[Boolean]("tfa_withdrawal"),
            row[Boolean]("tfa_login"),
            row[Option[String]]("tfa_secret"),
            row[Option[Symbol]]("tfa_type")
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

  def findUserByEmailAndPassword(email: String, password: String): Option[SocialUser] = DB.withConnection(db) { implicit c =>
    frontend.findUserByEmailAndPassword.on(
      'email -> email,
      'password -> password
    )().map(row =>
        new SocialUser(
          row[Long]("id"),
          row[String]("email"),
          row[Int]("verification"),
          row[Boolean]("on_mailing_list"),
          row[Boolean]("tfa_withdrawal"),
          row[Boolean]("tfa_login"),
          row[Option[String]]("tfa_secret"),
          row[Option[Symbol]]("tfa_type")
        )
      ).headOption
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

  def blacklistTOTPToken(user: Long, token: String, expiration: Timestamp) = DB.withConnection(db) { implicit c =>
    frontend.blacklistTOTPToken.on(
      'user -> user,
      'token -> token,
      'expiration -> expiration
    ).execute()
  }

  def TOTPTokenIsBlacklisted(user: Long, token: String) = DB.withConnection(db) { implicit c =>
    frontend.TOTPTokenIsBlacklisted.on(
      'user -> user,
      'token -> token
    )().toList.length > 0
  }

  def saveUser(id: Long, email: String, onMailingList: Boolean) = DB.withConnection(db) { implicit c =>
    frontend.updateUser.on(
      'id -> id,
      'email -> email,
      'onMailingList -> onMailingList
    ).execute()
  }

  def userChangePass(id: Long, old_password: String, new_password: String) = DB.withConnection(db) { implicit c =>
    frontend.userChangePassword.on(
      'user_id -> id,
      'old_password -> old_password,
      'new_password -> new_password
    ).execute()
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

  def trustedActionStart(email: String, is_signup: Boolean) = DB.withConnection(db) { implicit c =>
    frontend.trustedActionStart.on(
      'email -> email,
      'is_signup -> is_signup
    )().map(row =>
        row[Boolean]("success")
      ).head
  }

  def genTFASecret(uid: Long, typ: String) = DB.withConnection(db) { implicit c =>
    val secret = TOTPSecret()
    // everything is off by default
    frontend.updateTfaSecret.on(
      'id -> uid,
      'secret -> secret.toBase32,
      'typ -> typ
    ).execute()
    secret
  }

  def turnOffTFA(uid: Long) = DB.withConnection(db) { implicit c =>
    frontend.turnoffTfa.on(
      'id -> uid
    ).execute()
  }

  def turnOnTFA(uid: Long) = DB.withConnection(db) { implicit c =>
    frontend.turnonTfa.on(
      'id -> uid
    ).execute()
  }

  def turnOffEmails(uid: Long) = DB.withConnection(db) { implicit c =>
    frontend.turnoffEmails.on(
      'id -> uid
    ).execute()
  }

  def turnOnEmails(uid: Long) = DB.withConnection(db) { implicit c =>
    frontend.turnonEmails.on(
      'id -> uid
    ).execute()
  }

}
