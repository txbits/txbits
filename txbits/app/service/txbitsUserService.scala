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

package service

import java.sql.{ PreparedStatement, Timestamp }
import securesocial.core._
import play.api.{ Play, Logger, Application }
import scala.Some
import play.libs.{ F, Akka }
import akka.actor.Cancellable
import org.postgresql.util.PSQLException

object txbitsUserService {

  implicit val implapplication = play.api.Play.current

  def find(id: Long): Option[SocialUser] = {
    globals.userModel.findUserById(id)
  }

  def userExists(email: String): Boolean = {
    globals.userModel.userExists(email)
  }

  def create(user: SocialUser, password: String, token: String, pgp: String, username: String): SocialUser = {
    val pgp_key = PGP.parsePublicKey(pgp).map(_._2)
    val user_id = globals.userModel.create(user.email, password, user.onMailingList, pgp_key, token, username)

    user_id match {
      case Some(id) => {
        if (Play.current.configuration.getBoolean("fakeexchange").get) {
          //TODO: give fake money of every currency
          val freeMoney = 100
          globals.userModel.addFakeMoney(id, "USD", freeMoney)
          globals.userModel.addFakeMoney(id, "CAD", freeMoney)
        }
        user.copy(id = id, pgp = pgp_key)
      }
      case None => throw new Exception(" Duplicate Email ")
    }
  }

  def save(user: SocialUser): SocialUser = {
    globals.userModel.saveUser(user.id, user.email, user.onMailingList, user.username)
    user
  }

  // this function requires higher database privileges
  def resetPass(email: String, token: String, password: String) {
    globals.userModel.userResetPassComplete(email, token, password)
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
