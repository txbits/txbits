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
import securesocial.core.{ Token, PasswordInfo, SocialUser }
import service.{ SQLText, TOTPSecret }
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

  def create(email: String, password: String, hasher: String, onMailingList: Boolean) = DB.withConnection(db) { implicit c =>
    SQLText.createUser.on(
      'email -> email,
      'password -> password,
      'hasher -> hasher,
      'onMailingList -> onMailingList
    ).executeInsert[Option[Long]]()
  }

  def addFakeMoney(uid: Long, currency: String, amount: BigDecimal) = DB.withConnection(db) { implicit c =>
    if (Play.current.configuration.getBoolean("fakeexchange").get) {
      SQLText.addFakeMoney.on(
        'uid -> uid,
        'currency -> currency,
        'amount -> amount.bigDecimal
      ).executeUpdate() > 0
    } else {
      false
    }
  }

  def subtractFakeMoney(uid: Long, currency: String, amount: BigDecimal) = DB.withConnection(db) { implicit c =>
    if (Play.current.configuration.getBoolean("fakeexchange").get) {
      SQLText.removeFakeMoney.on(
        'uid -> uid,
        'currency -> currency,
        'amount -> amount.bigDecimal
      ).executeUpdate() > 0
    } else {
      false
    }
  }

  def findUserById(id: Long): Option[SocialUser] =
    DB.withConnection(db) { implicit c =>
      SQLText.findUserById.on(
        'id -> id
      )().map(row =>
          new SocialUser(
            row[Long]("id"),
            row[String]("email"),
            PasswordInfo(row[String]("hasher"), row[String]("password")),
            row[Int]("verification"),
            row[Boolean]("on_mailing_list"),
            row[Boolean]("tfa_withdrawal"),
            row[Boolean]("tfa_login"),
            row[Option[String]]("tfa_secret"),
            row[Option[Symbol]]("tfa_type")
          )
        ).headOption
    }

  def findUserByEmail(email: String): Option[SocialUser] = DB.withConnection(db) { implicit c =>
    SQLText.findUserByEmail.on(
      'email -> email
    )().map(row =>
        new SocialUser(
          row[Long]("id"),
          row[String]("email"),
          PasswordInfo(row[String]("hasher"), row[String]("password")),
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
    SQLText.tradeHistory.on(
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
    SQLText.depositWithdrawHistory.on(
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
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation
   *
   * @param token The token to save
   * @return A string with a uuid that will be embedded in the welcome email.
   */
  def saveToken(token: Token) =
    DB.withConnection(db) { implicit c =>
      SQLText.saveToken.on(
        'email -> token.email,
        'token -> token.uuid,
        'creation -> new Timestamp(token.creationTime.getMillis),
        'expiration -> new Timestamp(token.expirationTime.getMillis),
        'is_signup -> token.isSignUp
      ).execute
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
    SQLText.findToken.on(
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
    SQLText.deleteToken.on(
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
    SQLText.deleteExpiredTokens.execute()
  }

  def deleteExpiredTOTPBlacklistTokens() = DB.withConnection(db) { implicit c =>
    SQLText.deleteExpiredTOTPBlacklistTokens.execute()
  }

  def blacklistTOPTToken(user: BigDecimal, token: String, expiration: Timestamp) = DB.withConnection(db) { implicit c =>
    SQLText.blacklistTOPTToken.on(
      'user -> user,
      'token -> token,
      'expiration -> expiration
    ).execute()
  }

  def TOPTTokenIsBlacklisted(user: BigDecimal, token: String) = DB.withConnection(db) { implicit c =>
    SQLText.TOPTTokenIsBlacklisted.on(
      'user -> user,
      'token -> token
    )().toList.length > 0
  }

  def saveUser(id: Long, email: String, password: String, hasher: String, onMailingList: Boolean) = DB.withConnection(db) { implicit c =>
    SQLText.updateUser.on(
      'id -> id,
      'email -> email,
      'password -> password,
      'hasher -> hasher,
      'onMailingList -> onMailingList
    ).executeUpdate()
  }

  def genTFASecret(uid: Long, typ: String) = DB.withConnection(db) { implicit c =>
    val secret = TOTPSecret()
    // everything is off by default
    if (SQLText.updateTfaSecret.on(
      'id -> uid,
      'secret -> secret.toBase32,
      'typ -> typ
    ).executeUpdate() > 0) {
      Some(secret)
    } else {
      None
    }
  }

  def turnOffTFA(uid: Long) = DB.withConnection(db) { implicit c =>
    SQLText.turnoffTfa.on(
      'id -> uid
    ).executeUpdate() > 0
  }

  def turnOnTFA(uid: Long) = DB.withConnection(db) { implicit c =>
    SQLText.turnonTfa.on(
      'id -> uid
    ).executeUpdate() > 0
  }

  def turnOffEmails(uid: Long) = DB.withConnection(db) { implicit c =>
    SQLText.turnoffEmails.on(
      'id -> uid
    ).executeUpdate() > 0
  }

  def turnOnEmails(uid: Long) = DB.withConnection(db) { implicit c =>
    SQLText.turnonEmails.on(
      'id -> uid
    ).executeUpdate() > 0
  }

}
