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

  val trustedActionTimer = system.scheduler.schedule(30.seconds, 30.seconds)(processTrustedActionRequests())

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
            Mailer.sendAlreadyRegisteredEmail(email)
          }
          case false => {
            val token = createToken(email, isSignUp = true)
            Mailer.sendSignUpEmail(email, token)
          }
        }
      } else {
        Mailer.sendPasswordResetEmail(email, token)
      }
      // remove the token from the queue
      model.trustedActionFinish(email, is_signup)
    }
  }

  private def createToken(email: String, isSignUp: Boolean) = {
    val tokenId = use[IdGenerator].generate
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

  def receive = {
    case _ =>
  }
}

object UserTrustService {
  def props(model: UserTrustModel) = {
    Props(classOf[UserTrustService], model)
  }
}