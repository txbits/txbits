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
import play.api.mvc.{ Result, Action, Controller }
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import play.api.{ Play, Logger }
import securesocial.core._
import com.typesafe.plugin._
import Play.current
import securesocial.core.providers.utils._
import org.joda.time.DateTime
import play.api.i18n.Messages
import scala.language.reflectiveCalls
import securesocial.core.Token
import scala.Some
import securesocial.core.SocialUser
import service.txbitsUserService
import models.{ LogType, LogEvent }
import java.security.SecureRandom

/**
 * A controller to handle user registration.
 *
 */
object Registration extends Controller {

  val PasswordsDoNotMatch = "securesocial.signup.passwordsDoNotMatch"
  val ThankYouCheckEmail = "securesocial.signup.thankYouCheckEmail"
  val InvalidLink = "securesocial.signup.invalidLink"
  val SignUpDone = "securesocial.signup.signUpDone"
  val PasswordUpdated = "securesocial.password.passwordUpdated"
  val ErrorUpdatingPassword = "securesocial.password.error"

  val MailingList = "mailinglist"
  val Password = "password"
  val Password1 = "password1"
  val Password2 = "password2"
  val Email = "email"
  val Success = "success"
  val Error = "error"

  val TokenDurationKey = "securesocial.userpass.tokenDuration"
  val RegistrationEnabled = "securesocial.registrationEnabled"
  val DefaultDuration = 60
  val TokenDuration = Play.current.configuration.getInt(TokenDurationKey).getOrElse(DefaultDuration)

  /** The redirect target of the handleStartSignUp action. */
  val onHandleStartSignUpGoTo = stringConfig("securesocial.onStartSignUpGoTo", controllers.routes.LoginPage.login().url)
  /** The redirect target of the handleSignUp action. */
  val onHandleSignUpGoTo = stringConfig("securesocial.onSignUpGoTo", controllers.routes.LoginPage.login().url)
  /** The redirect target of the handleStartResetPassword action. */
  val onHandleStartResetPasswordGoTo = stringConfig("securesocial.onStartResetPasswordGoTo", controllers.routes.LoginPage.login().url)
  /** The redirect target of the handleResetPassword action. */
  val onHandleResetPasswordGoTo = stringConfig("securesocial.onResetPasswordGoTo", controllers.routes.LoginPage.login().url)

  lazy val registrationEnabled = current.configuration.getBoolean(RegistrationEnabled).getOrElse(true)

  private def stringConfig(key: String, default: => String) = {
    Play.current.configuration.getString(key).getOrElse(default)
  }

  case class RegistrationInfo(mailingList: Boolean, password: String)

  val form = Form[RegistrationInfo](
    mapping(
      MailingList -> boolean,
      Password ->
        tuple(
          Password1 -> nonEmptyText.verifying(PasswordValidator.validator),
          Password2 -> nonEmptyText
        ).verifying(Messages(PasswordsDoNotMatch), passwords => passwords._1 == passwords._2)
    ) // binding
    ((list, password) => RegistrationInfo(list, password._1)) // unbinding
    (info => Some(info.mailingList, ("", "")))
  )

  val startForm = Form(
    Email -> email.verifying(nonEmpty)
  )

  val changePasswordForm = Form(
    Password ->
      tuple(
        Password1 -> nonEmptyText.verifying(PasswordValidator.validator),
        Password2 -> nonEmptyText
      ).verifying(Messages(PasswordsDoNotMatch), passwords => passwords._1 == passwords._2)
  )

  /**
   * Starts the sign up process
   */
  def startSignUp = Action { implicit request =>
    if (registrationEnabled) {
      if (SecureSocial.enableRefererAsOriginalUrl) {
        SecureSocial.withRefererAsOriginalUrl(Ok(SecureSocialTemplates.getStartSignUpPage(request, startForm)))
      } else {
        Ok(SecureSocialTemplates.getStartSignUpPage(request, startForm))
      }
    } else NotFound(views.html.defaultpages.notFound.render(request, None))
  }

  val random = new SecureRandom()

  private def createToken(email: String, isSignUp: Boolean): (String, Token) = {
    val tokenId = use[IdGenerator].generate
    val now = DateTime.now

    val token = Token(
      tokenId, email,
      now,
      now.plusMinutes(TokenDuration),
      isSignUp = isSignUp
    )
    txbitsUserService.save(token)
    (tokenId, token)
  }

  def handleStartSignUp = Action { implicit request =>
    if (registrationEnabled) {
      startForm.bindFromRequest.fold(
        errors => {
          BadRequest(SecureSocialTemplates.getStartSignUpPage(request, errors))
        },
        email => {
          // check if there is already an account for this email address
          txbitsUserService.findByEmail(email) match {
            case Some(user) => {
              // user signed up already, send an email offering to login/recover password
              Mailer.sendAlreadyRegisteredEmail(user)
            }
            case None => {
              val token = createToken(email, isSignUp = true)
              Mailer.sendSignUpEmail(email, token._1)
            }
          }
          Redirect(onHandleStartSignUpGoTo).flashing(Success -> Messages(ThankYouCheckEmail), Email -> email)
        }
      )
    } else NotFound(views.html.defaultpages.notFound.render(request, None))
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
        Ok(SecureSocialTemplates.getSignUpPage(request, form, token))
      })
    } else NotFound(views.html.defaultpages.notFound.render(request, None))
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
            BadRequest(SecureSocialTemplates.getSignUpPage(request, errors, t.uuid))
          },
          info => {
            val user = txbitsUserService.create(SocialUser(
              -1, // this is a placeholder
              t.email,
              Registry.hashers.currentHasher.hash(info.password),
              0, //not verified
              info.mailingList
            ))
            txbitsUserService.deleteToken(t.uuid)
            if (UsernamePasswordProvider.sendWelcomeEmail) {
              Mailer.sendWelcomeEmail(user)
            }
            globals.logModel.logEvent(LogEvent.fromRequest(Some(user.id), Some(user.email), request, LogType.SignupSuccess))
            if (UsernamePasswordProvider.signupSkipLogin) {
              ProviderController.completeAuthentication(user, request2session)
            } else {
              Redirect(onHandleSignUpGoTo).flashing(Success -> Messages(SignUpDone)).withSession(request2session)
            }
          }
        )
      })
    } else NotFound(views.html.defaultpages.notFound.render(request, None))
  }

  def startResetPassword = Action { implicit request =>
    Ok(SecureSocialTemplates.getStartResetPasswordPage(request, startForm))
  }

  def handleStartResetPassword = Action { implicit request =>
    startForm.bindFromRequest.fold(
      errors => {
        BadRequest(SecureSocialTemplates.getStartResetPasswordPage(request, errors))
      },
      email => {
        txbitsUserService.findByEmail(email) match {
          case Some(user) => {
            val token = createToken(email, isSignUp = false)
            Mailer.sendPasswordResetEmail(user, token._1)
          }
          case None => {
            // The user wasn't registered. Oh, well.
          }
        }
        Redirect(onHandleStartResetPasswordGoTo).flashing(Success -> Messages(ThankYouCheckEmail))
      }
    )
  }

  def resetPassword(token: String) = Action { implicit request =>
    executeForToken(token, false, { t =>
      Ok(SecureSocialTemplates.getResetPasswordPage(request, changePasswordForm, token))
    })
  }

  def handleResetPassword(token: String) = Action { implicit request =>
    executeForToken(token, false, { t =>
      changePasswordForm.bindFromRequest.fold(errors => {
        BadRequest(SecureSocialTemplates.getResetPasswordPage(request, errors, token))
      },
        p => {
          val (toFlash, eventSession) = txbitsUserService.findByEmail(t.email) match {
            case Some(user) => {
              val hashed = Registry.hashers.currentHasher.hash(p._1)
              val updated = txbitsUserService.save(user.copy(passwordInfo = hashed))
              txbitsUserService.deleteToken(token)
              Mailer.sendPasswordChangedNotice(updated)
              (Success -> Messages(PasswordUpdated), updated)
            }
            case _ => {
              Logger.error("[securesocial] could not find user with email %s during password reset".format(t.email))
              (Error -> Messages(ErrorUpdatingPassword), None)
            }
          }
          Redirect(onHandleResetPasswordGoTo).flashing(toFlash)
        })
    })
  }
}
