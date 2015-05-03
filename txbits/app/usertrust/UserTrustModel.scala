// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package usertrust

import play.api.db.DB
import securesocial.core.Token
import java.sql.Timestamp
import play.api.Play.current
import models.Withdrawal
import org.joda.time.DateTime

class UserTrustModel(val db: String = "default") {
  def getTrustedActionRequests = DB.withConnection(db) { implicit c =>
    SQLText.getTrustedActionRequests().map(row =>
      (row[String]("email"), row[Boolean]("is_signup"))
    ).toList
  }

  def getPendingWithdrawalRequests = DB.withConnection(db) { implicit c =>
    SQLText.getPendingWithdrawalRequests().map(row =>
      (
        Withdrawal(
          row[Long]("id"),
          row[BigDecimal]("amount").bigDecimal.toPlainString,
          row[BigDecimal]("fee").bigDecimal.toPlainString,
          row[DateTime]("created"),
          "",
          row[String]("currency")
        ),
          row[String]("email"),
          row[Option[String]]("pgp"),
          row[Option[String]]("destination")
      )
    ).toList
  }

  def saveWithdrawalToken(id: Long, token: String, expiration: DateTime) = DB.withConnection(db) { implicit c =>
    SQLText.saveWithdrawalToken.on('id -> id, 'token -> token, 'expiration -> new Timestamp(expiration.getMillis)).execute
  }

  def trustedActionFinish(email: String, is_signup: Boolean) = DB.withConnection(db) { implicit c =>
    SQLText.trustedActionProcessed.on('email -> email, 'is_signup -> is_signup).execute
  }

  def saveToken(token: Token) = DB.withConnection(db) { implicit c =>
    SQLText.saveToken.on(
      'email -> token.email,
      'token -> token.uuid,
      'creation -> new Timestamp(token.creationTime.getMillis),
      'expiration -> new Timestamp(token.expirationTime.getMillis),
      'is_signup -> token.isSignUp
    ).execute
  }
}
