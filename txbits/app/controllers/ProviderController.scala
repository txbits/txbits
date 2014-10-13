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
    single("token" -> nonEmptyText)
  )

  private def badRequestTOTP[A](f: Form[String], request: Request[A], msg: Option[String] = None): Result = {
    BadRequest(SecureSocialTemplates.getTFATOTPPage(request, f, msg))
  }

  //TODO: turn into ajax call
  def tfaPost() = TFAAction { implicit request =>
    val form = tfaForm.bindFromRequest()(request.request)
    form.fold(
      errors => {
        request.user.TFAType match {
          case Some('TOTP) => badRequestTOTP(errors, request.request)
          case None => Results.BadRequest("Two factor auth not configured.") //TODO: take the user to an actual error page
        }
      },
      tfaToken => {
        if (request.user.TFALogin) {
          request.user.TFAType match {
            case Some('TOTP) => {
              if (Authorization2fa.verifyTokenForUser(request.user.id, tfaToken, request.user.TFASecret.get)) {
                val authenticator = SecureSocial.authenticatorFromRequest(request)
                Authenticator.save(authenticator.get.complete2fa)
                globals.logModel.logEvent(LogEvent.fromRequest(Some(request.user.id), Some(request.user.email), request.request, LogType.LoginSuccess))
                Redirect(toUrl(request2session)).withSession(request2session - SecureSocial.OriginalUrlKey)
              } else {
                // form error
                badRequestTOTP(tfaForm, request.request, Some("Invalid token."))
              }
            }
            case None => Results.BadRequest("You shouldn't be on this page. You don't have two factor auth enabled for login.") //TODO: maybe just redirect to homepage?
            case _ => Results.BadRequest("Unknown two factor auth type.") //TODO: log this error. We dun goof'd
          }
        } else {
          Results.BadRequest("Two factor auth not configured.")
        }
      }
    )
  }

  def loginPost() = CSRFCheck {
    Action { implicit request =>
      try {
        Registry.provider.authenticate().fold(result => result, {
          user => completeAuthentication(user, request2session)
        })
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

  def completeAuthentication(user: SocialUser, session: Session)(implicit request: RequestHeader): Result = {
    if (Logger.isDebugEnabled) {
      Logger.debug("[securesocial] user logged in : [" + user + "]")
    }
    Authenticator.create(user) match {
      case Right(authenticator) => {
        if (authenticator.needsTFA) {
          globals.logModel.logEvent(LogEvent.fromRequest(Some(user.id), Some(user.email), request, LogType.LoginPartialSuccess))
          Redirect(user.TFAType match {
            case Some('TOTP) => controllers.routes.LoginPage.tfaTOTP()
          }).withSession(session).withCookies(authenticator.toCookie)
        } else {
          globals.logModel.logEvent(LogEvent.fromRequest(Some(user.id), Some(user.email), request, LogType.LoginSuccess))
          Redirect(toUrl(session)).withSession(session -
            SecureSocial.OriginalUrlKey).withCookies(authenticator.toCookie)
        }
      }
      case Left(error) => {
        // improve this
        throw new RuntimeException("Error creating authenticator")
      }
    }
  }
}
