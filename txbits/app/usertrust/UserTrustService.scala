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

package usertrust

import java.io.File

import play.Environment
import play.api.i18n._
import securesocial.core.providers.utils.Mailer
import securesocial.core.{ IdGenerator, Token }
import org.joda.time.DateTime
import play.api.{ Mode, Play }
import akka.actor.{ Actor, Props }
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import service.txbitsUserService
import play.api.Play.current

class UserTrustService(val model: UserTrustModel) extends Actor {
  import context.system
  implicit val ec: ExecutionContext = system.dispatcher

  val TokenDurationKey = "securesocial.userpass.tokenDuration"
  val DefaultDuration = 60
  val TokenDuration = Play.current.configuration.getInt(TokenDurationKey).getOrElse(DefaultDuration)

  val trustedActionTimer = system.scheduler.schedule(15.seconds, 15.seconds)(processTrustedActionRequests())

  // Warning: It is not safe to have two user trust services running at the same time
  def processTrustedActionRequests() {

    // XXX: temporary hack to make Messages work in emails (only english for now)
    implicit val messages = new Messages(new Lang("en", "US"), new DefaultMessagesApi(play.api.Environment.simple(new File("."), Mode.Prod),
      play.api.Play.current.configuration,
      new DefaultLangs(play.api.Play.current.configuration))
    )

    for ((email, is_signup, language) <- model.getTrustedActionRequests) {
      // send email to the user
      if (is_signup) {
        // check if there is already an account for this email address
        txbitsUserService.userExists(email) match {
          case true => {
            // user signed up already, send an email offering to login/recover password
            Mailer.sendAlreadyRegisteredEmail(email, globals.userModel.userPgpByEmail(email))
          }
          case false => {
            val token = createToken(email, isSignUp = is_signup, language)
            Mailer.sendSignUpEmail(email, token)
          }
        }
      } else {
        // create and save token
        val token = createToken(email, isSignUp = is_signup, language)
        Mailer.sendPasswordResetEmail(email, token, globals.userModel.userPgpByEmail(email))
      }
      // remove the token from the queue
      model.trustedActionFinish(email, is_signup)
    }
    for ((withdrawal, email, pgp, destination) <- model.getPendingWithdrawalRequests) {
      // create and save token
      val token = createWithdrawalToken(withdrawal.id)
      // send withdrawal confirmation email
      Mailer.sendWithdrawalConfirmEmail(email, withdrawal.amount, withdrawal.currency, destination.getOrElse("unknown"), withdrawal.id, token, pgp)
    }
  }

  private def createToken(email: String, isSignUp: Boolean, language: String) = {
    val tokenId = IdGenerator.generateEmailToken
    val now = DateTime.now

    val token = Token(
      tokenId, email,
      now,
      now.plusMinutes(TokenDuration),
      isSignUp = isSignUp,
      language
    )

    model.saveToken(token)

    tokenId
  }

  private def createWithdrawalToken(id: Long) = {
    val token = IdGenerator.generateEmailToken
    val expiration = DateTime.now.plusMinutes(TokenDuration)
    model.saveWithdrawalToken(id, token, expiration)

    token
  }

  def receive = {
    case _ =>
  }
}

object UserTrustService {
  def props(model: UserTrustModel) = {
    Props(classOf[UserTrustService], model)
  }
}