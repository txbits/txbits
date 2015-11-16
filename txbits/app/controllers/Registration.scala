/**
 * Copyright 2012-2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package controllers

import _root_.java.util.UUID
import javax.inject.Inject
import play.api.mvc.{ Result, Action, Controller }
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.{ Play, Logger }
import play.api.i18n.MessagesApi
import securesocial.core._
import Play.current
import securesocial.core.providers.utils._
import org.joda.time.DateTime
import play.api.i18n.{ I18nSupport, Messages }
import scala.language.reflectiveCalls
import securesocial.core.Token
import scala.Some
import securesocial.core.SocialUser
import service.{ PGP, txbitsUserService }
import models.{ LogType, LogEvent }
import java.security.SecureRandom

/**
 * A controller to handle user registration.
 *
 */
class Registration @Inject() (val messagesApi: MessagesApi) extends Controller with I18nSupport {
  import controllers.Registration._
  import PasswordChange._

  lazy val registrationEnabled = current.configuration.getBoolean(RegistrationEnabled).getOrElse(true)

  val form = Form[RegistrationInfo](
    mapping(
      AcceptTos -> boolean.verifying(acceptTos => acceptTos),
      MailingList -> boolean,
      Password ->
        tuple(
          Password1 -> nonEmptyText.verifying(Messages(passwordErrorStr, passwordMinLen), passwordErrorFunc _),
          Password2 -> nonEmptyText
        ).verifying(Messages(PasswordsDoNotMatch), passwords => passwords._1 == passwords._2),
      Pgp -> text.verifying(Messages(PgpKeyInvalid), pgp => pgp == "" || PGP.parsePublicKey(pgp).isDefined)
    ) // binding
    ((_, list, password, pgp) => RegistrationInfo(list, password._1, pgp)) // unbinding
    (info => Some(true, info.mailingList, ("", ""), info.pgp))
  )

  val emailForm = Form(
    Email -> email.verifying(nonEmpty)
  )

  val startForm = Form[StartRegistrationInfo](
    mapping(
      Email -> email.verifying(nonEmpty),
      Language -> text.verifying(nonEmpty)
    ) // binding
    ((email, language) => StartRegistrationInfo(email, language)) // unbinding
    (info => Some(info.email, info.language))
  )

  val changePasswordForm = Form(
    Password ->
      tuple(
        Password1 -> nonEmptyText.verifying(Messages(passwordErrorStr, passwordMinLen), passwordErrorFunc _),
        Password2 -> nonEmptyText
      ).verifying(Messages(PasswordsDoNotMatch), passwords => passwords._1 == passwords._2)
  )

  /**
   * Starts the sign up process
   */
  def startSignUp = Action { implicit request =>
    if (registrationEnabled) {
      if (SecureSocial.enableRefererAsOriginalUrl) {
        SecureSocial.withRefererAsOriginalUrl(Ok(views.html.auth.Registration.startSignUp(startForm)))
      } else {
        Ok(views.html.auth.Registration.startSignUp(startForm))
      }
    } else NotFound
  }

  val random = new SecureRandom()

  def handleStartSignUp = Action { implicit request =>
    if (registrationEnabled) {
      startForm.bindFromRequest.fold(
        errors => {
          BadRequest(views.html.auth.Registration.startSignUp(errors))
        },
        form => {
          globals.userModel.trustedActionStart(form.email, isSignup = true, form.language)
          Redirect(onHandleStartSignUpGoTo).flashing(Success -> Messages(ThankYouCheckEmail), Email -> form.email)
        }
      )
    } else NotFound
  }

  /**
   * Renders the sign up page
   * @return
   */
  def signUp(token: String) = Action { implicit request =>
    if (registrationEnabled) {
      if (Logger.isDebugEnabled) {
        Logger.debug("[securesocial] trying sign up with token %s".format(token))
      }
      executeForToken(token, true, { _ =>
        Ok(views.html.auth.Registration.signUp(form, token))
      })
    } else NotFound
  }

  private def executeForToken(token: String, isSignUp: Boolean, f: Token => Result): Result = {
    txbitsUserService.findToken(token) match {
      case Some(t) if !t.isExpired && t.isSignUp == isSignUp => {
        f(t)
      }
      case _ => {
        val to = if (isSignUp) controllers.routes.Registration.startSignUp() else controllers.routes.Registration.startResetPassword()
        Redirect(to).flashing(Error -> Messages(InvalidLink))
      }
    }
  }

  // XXX: copied from ProviderController TODO: fix duplication
  def completePasswordAuth[A](id: Long, email: String)(implicit request: play.api.mvc.Request[A]) = {
    import controllers.ProviderController._
    val authenticator = Authenticator.create(Some(id), None, email)
    Redirect(toUrl(request2session)).withSession(request2session - SecureSocial.OriginalUrlKey).withCookies(authenticator.toCookie)
  }

  /**
   * Handles posts from the sign up page
   */
  def handleSignUp(token: String) = Action { implicit request =>
    if (registrationEnabled) {
      executeForToken(token, true, { t =>
        form.bindFromRequest.fold(
          errors => {
            if (Logger.isDebugEnabled) {
              Logger.debug("[securesocial] errors " + errors)
            }
            BadRequest(views.html.auth.Registration.signUp(errors, t.uuid))
          },
          info => {
            val user = txbitsUserService.create(SocialUser(
              -1, // this is a placeholder
              t.email,
              0, //not verified
              t.language,
              info.mailingList
            ), info.password, token, info.pgp)
            txbitsUserService.deleteToken(t.uuid)
            if (UsernamePasswordProvider.sendWelcomeEmail) {
              Mailer.sendWelcomeEmail(user)
            }
            globals.logModel.logEvent(LogEvent.fromRequest(Some(user.id), Some(user.email), request, LogType.SignupSuccess))
            if (UsernamePasswordProvider.signupSkipLogin) {
              completePasswordAuth(user.id, user.email)
            } else {
              Redirect(onHandleSignUpGoTo).flashing(Success -> Messages(SignUpDone)).withSession(request2session)
            }
          }
        )
      })
    } else NotFound
  }

  def startResetPassword = Action { implicit request =>
    Ok(views.html.auth.Registration.startResetPassword(emailForm))
  }

  def handleStartResetPassword = Action { implicit request =>
    emailForm.bindFromRequest.fold(
      errors => {
        BadRequest(views.html.auth.Registration.startResetPassword(errors))
      },
      email => {
        txbitsUserService.userExists(email) match {
          case true => {
            globals.userModel.trustedActionStart(email, isSignup = false, "")
          }
          case false => {
            // The user wasn't registered. Oh, well.
          }
        }
        Redirect(onHandleStartResetPasswordGoTo).flashing(Success -> Messages(ThankYouCheckEmail))
      }
    )
  }

  def resetPassword(token: String) = Action { implicit request =>
    executeForToken(token, false, { t =>
      Ok(views.html.auth.Registration.resetPasswordPage(changePasswordForm, token))
    })
  }

  def handleResetPassword(token: String) = Action { implicit request =>
    executeForToken(token, false, { t =>
      changePasswordForm.bindFromRequest.fold(errors => {
        BadRequest(views.html.auth.Registration.resetPasswordPage(errors, token))
      },
        p => {
          val toFlash = txbitsUserService.userExists(t.email) match {
            case true => {
              // this should never actually fail because we checked the token already
              txbitsUserService.resetPass(t.email, token, p._1)
              txbitsUserService.deleteToken(token)
              Mailer.sendPasswordChangedNotice(t.email, globals.userModel.userPgpByEmail(t.email))
              Success -> Messages(PasswordUpdated)
            }
            case false => {
              Logger.error("[securesocial] could not find user with email %s during password reset".format(t.email))
              Error -> Messages(ErrorUpdatingPassword)
            }
          }
          Redirect(onHandleResetPasswordGoTo).flashing(toFlash)
        })
    })
  }
}

object Registration {

  val PasswordsDoNotMatch = "auth.signup.passwordsDoNotMatch"
  val PgpKeyInvalid = "auth.signup.pgpKeyInvalid"
  val ThankYouCheckEmail = "auth.signup.thankYouCheckEmail"
  val InvalidLink = "auth.signup.invalidLink"
  val SignUpDone = "auth.signup.signUpDone"
  val PasswordUpdated = "auth.password.passwordUpdated"
  val ErrorUpdatingPassword = "auth.password.error"

  val AcceptTos = "accepttos"
  val MailingList = "mailinglist"
  val Password = "password"
  val Password1 = "password1"
  val Password2 = "password2"
  val Email = "email"
  val Success = "success"
  val Error = "error"
  val Pgp = "pgp"
  val Language = "language"

  val RegistrationEnabled = "securesocial.registrationEnabled"

  /** The redirect target of the handleStartSignUp action. */
  val onHandleStartSignUpGoTo = stringConfig("securesocial.onStartSignUpGoTo", controllers.routes.LoginPage.login().url)
  /** The redirect target of the handleSignUp action. */
  val onHandleSignUpGoTo = stringConfig("securesocial.onSignUpGoTo", controllers.routes.LoginPage.login().url)
  /** The redirect target of the handleStartResetPassword action. */
  val onHandleStartResetPasswordGoTo = stringConfig("securesocial.onStartResetPasswordGoTo", controllers.routes.LoginPage.login().url)
  /** The redirect target of the handleResetPassword action. */
  val onHandleResetPasswordGoTo = stringConfig("securesocial.onResetPasswordGoTo", controllers.routes.LoginPage.login().url)

  private def stringConfig(key: String, default: => String) = {
    Play.current.configuration.getString(key).getOrElse(default)
  }

  case class RegistrationInfo(mailingList: Boolean, password: String, pgp: String)
  case class StartRegistrationInfo(email: String, language: String)
}
