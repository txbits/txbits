package service

import java.sql.{ PreparedStatement, Timestamp }
import securesocial.core._
import play.api.{ Play, Logger, Application }
import play.api.db._
import org.joda.time.DateTime
import scala.Some
import anorm.SqlParser._
import securesocial.core.PasswordInfo
import scala.Some
import anorm.~
import play.libs.Akka
import akka.actor.Cancellable
import org.postgresql.util.PSQLException

object txbitsUserService {

  implicit val implapplication = play.api.Play.current

  def find(id: Long): Option[SocialUser] = {
    globals.userModel.findUserById(id)
  }

  /**
   * Finds a user by email and provider id.
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation.
   *
   * @param email - the user email
   * @return
   */
  def findByEmail(email: String): Option[SocialUser] = {
    globals.userModel.findUserByEmail(email)
  }

  def create(user: SocialUser): SocialUser = {
    val user_id = globals.userModel.create(user.email, user.passwordInfo.password,
      user.passwordInfo.hasher, user.onMailingList)

    user_id match {
      case Some(id) => {
        if (Play.current.configuration.getBoolean("fakeexchange").get) {
          //TODO: give fake money of every currency
          val freeMoney = 100
          globals.userModel.addFakeMoney(id, "USD", freeMoney)
          globals.userModel.addFakeMoney(id, "CAD", freeMoney)
        }
        user.copy(id = id)
      }
      case None => throw new Exception(" Duplicate Email ")
    }
  }

  def save(user: SocialUser): SocialUser = {
    globals.userModel.saveUser(user.id, user.email, user.passwordInfo.password,
      user.passwordInfo.hasher, user.onMailingList)
    user
  }

  /**
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation
   *
   * @param token The token to save
   * @return A string with a uuid that will be embedded in the welcome email.
   */
  def save(token: Token) = {
    globals.userModel.saveToken(token)
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
  def findToken(token: String): Option[Token] = {
    globals.userModel.findToken(token)
  }

  /**
   * Deletes a token
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation
   *
   * @param uuid the token id
   */
  def deleteToken(uuid: String) {
    globals.userModel.deleteToken(uuid)
  }

  /**
   * Deletes all expired tokens
   *
   * Note: If you do not plan to use the UsernamePassword provider just provide en empty
   * implementation
   *
   */
  def deleteExpiredTokens() {
    globals.userModel.deleteExpiredTokens()
    globals.userModel.deleteExpiredTOTPBlacklistTokens()
  }

  var cancellable: Option[Cancellable] = None
  val DefaultInterval = 5
  val DeleteIntervalKey = "securesocial.userpass.tokenDeleteInterval"

  def onStart() {
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits._
    val i = play.api.Play.current.configuration.getInt(DeleteIntervalKey).getOrElse(DefaultInterval)

    cancellable = if (UsernamePasswordProvider.enableTokenJob) {
      Some(
        Akka.system.scheduler.schedule(1.seconds, i.minutes) {
          if (Logger.isDebugEnabled) {
            Logger.debug("[securesocial] calling deleteExpiredTokens()")
          }
          try {
            deleteExpiredTokens()
          } catch {
            case e: PSQLException => // Ignore failures (they can be caused by a connection that just closed and hopefully next time we'll get a valid connection
          }
        }
      )
    } else None

    Logger.info("[securesocial] loaded user service: %s".format(this.getClass))
  }

  def onStop() {
    cancellable.map(_.cancel())
  }
}
