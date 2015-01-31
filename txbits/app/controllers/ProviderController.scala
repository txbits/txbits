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

import models.{ LogEvent, LogType }
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{ Result, _ }
import play.api.{ Logger, Play }
import securesocial.core.{ AccessDeniedException, SocialUser, _ }
import service.{ TOTPAuthenticator, TOTPSecret }
import play.filters.csrf._

/**
 * A controller to provide the authentication entry point
 */
object ProviderController extends Controller with securesocial.core.SecureSocial {
  /**
   * The property that specifies the page the user is redirected to if there is no original URL saved in
   * the session.
   */
  val onLoginGoTo = "securesocial.onLoginGoTo"

  /**
   * The root path
   */
  val Root = "/"

  /**
   * The application context
   */
  val ApplicationContext = "application.context"

  val InvalidCredentials = "securesocial.login.invalidCredentials"

  /**
   * Returns the url that the user should be redirected to after login
   *
   * @param session
   * @return
   */
  def toUrl(session: Session) = session.get(SecureSocial.OriginalUrlKey).getOrElse(landingUrl)

  /**
   * The url where the user needs to be redirected after succesful authentication.
   *
   * @return
   */
  def landingUrl = Play.configuration.getString(onLoginGoTo).getOrElse(
    Play.configuration.getString(ApplicationContext).getOrElse(Root)
  )

  /**
   * Renders a not authorized page if the Authorization object passed to the action does not allow
   * execution.
   *
   * @see Authorization
   */
  def notAuthorized() = Action { implicit request =>
    Forbidden(SecureSocialTemplates.getNotAuthorizedPage)
  }

  val tfaForm = Form(
    single("token" -> text)
  )

  private def badRequestTOTP[A](f: Form[String], request: Request[A], msg: Option[String] = None): Result = {
    BadRequest(SecureSocialTemplates.getTFATOTPPage(request, f, msg))
  }

  //TODO: turn into ajax call
  def tfaPost() = Action { implicit request =>
    val form = tfaForm.bindFromRequest()(request)
    form.fold(
      errors => {
        Results.BadRequest("Unknown form error.") //TODO: take the user to an actual error page
      },
      tfaToken => {
        val authenticator = SecureSocial.authenticatorFromRequest(request)
        if (authenticator.isDefined) {
          if (globals.userModel.userHasTotp(authenticator.get.email)) {
            val user = globals.userModel.totpLoginStep2(authenticator.get.email, authenticator.get.totpSecret.get, tfaToken, models.LogModel.headersFromRequest(request), models.LogModel.ipFromRequest(request))
            if (user.isDefined) {
              Authenticator.save(authenticator.get.complete2fa(user.get.id))
              Redirect(toUrl(request2session)).withSession(request2session - SecureSocial.OriginalUrlKey)
            } else {
              // form error
              badRequestTOTP(tfaForm, request, Some("Invalid token."))
            }
          } else {
            Results.BadRequest("Two factor auth not configured.")
          }
        } else {
          Results.BadRequest("Please log in first.")
        }
      }
    )
  }

  private def badRequest[A](f: Form[(String, String)], request: Request[A], msg: Option[String] = None): Result = {
    Results.BadRequest(SecureSocialTemplates.getLoginPage(request, f, msg))
  }

  def completePasswordAuth[A](id: Long, email: String)(implicit request: Request[A]) = {
    // TODO: move log to db
    val authenticator = Authenticator.create(Some(id), None, email)
    Redirect(toUrl(request2session)).withSession(request2session - SecureSocial.OriginalUrlKey).withCookies(authenticator.toCookie)
  }

  def loginPost() = CSRFCheck {
    Action { implicit request =>
      try {
        val form = UsernamePasswordProvider.loginForm.bindFromRequest()
        form.fold(
          errors => badRequest(errors, request),
          credentials => {
            val email = credentials._1.trim
            var user: Option[SocialUser] = None
            var totp_hash: Option[String] = None
            // check for 2FA
            if (globals.userModel.userHasTotp(email)) {
              totp_hash = globals.userModel.totpLoginStep1(email, credentials._2, models.LogModel.headersFromRequest(request), models.LogModel.ipFromRequest(request))
            } else {
              user = globals.userModel.findUserByEmailAndPassword(email, credentials._2, models.LogModel.headersFromRequest(request), models.LogModel.ipFromRequest(request))
            }
            if (totp_hash.isDefined) {
              // create session
              val authenticator = Authenticator.create(None, totp_hash, email)
              Redirect(controllers.routes.LoginPage.tfaTOTP()).withSession(request2session).withCookies(authenticator.toCookie)
            } else if (user.isDefined) {
              // create session
              completePasswordAuth(user.get.id, email)
            } else {
              badRequest(UsernamePasswordProvider.loginForm, request, Some(InvalidCredentials))
            }
          }
        )
      } catch {
        case ex: AccessDeniedException => {
          Redirect(controllers.routes.LoginPage.login()).flashing("error" -> Messages("securesocial.login.accessDenied"))
        }

        case other: Throwable => {
          Logger.error("Unable to log user in. An exception was thrown", other)
          Redirect(controllers.routes.LoginPage.login()).flashing("error" -> Messages("securesocial.login.errorLoggingIn"))
        }
      }
    }
  }
}
