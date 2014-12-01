package usertrust

import play.api.db.DB
import securesocial.core.Token
import java.sql.Timestamp
import play.api.Play.current

class UserTrustModel(val db: String = "default") {
  def getTrustedActionRequests = DB.withConnection(db) { implicit c =>
    SQLText.getTrustedActionRequests().map(row =>
      (row[String]("email"), row[Boolean]("is_signup"))
    ).toList
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
