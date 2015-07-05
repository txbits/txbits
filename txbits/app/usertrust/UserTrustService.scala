// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package usertrust

import securesocial.core.providers.utils.Mailer
import securesocial.core.{ IdGenerator, Token }
import org.joda.time.DateTime
import com.typesafe.plugin.use
import play.api.Play
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
    for ((email, is_signup) <- model.getTrustedActionRequests) {
      // create and save token
      val token = createToken(email, isSignUp = is_signup)
      // send email to the user
      if (is_signup) {
        // check if there is already an account for this email address
        txbitsUserService.userExists(email) match {
          case true => {
            // user signed up already, send an email offering to login/recover password
            Mailer.sendAlreadyRegisteredEmail(email, globals.userModel.userPgpByEmail(email))
          }
          case false => {
            val token = createToken(email, isSignUp = true)
            Mailer.sendSignUpEmail(email, token)
          }
        }
      } else {
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

  private def createToken(email: String, isSignUp: Boolean) = {
    val tokenId = IdGenerator.generateEmailToken
    val now = DateTime.now

    val token = Token(
      tokenId, email,
      now,
      now.plusMinutes(TokenDuration),
      isSignUp = isSignUp
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