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
package securesocial.core

import play.api.mvc._
import play.api.i18n.Messages
import play.api.Logger
import play.api.libs.json.{ JsValue, Json }
import play.api.http.HeaderNames
import scala.concurrent.Future
import scala.Some
import play.api.mvc.Result
import play.api.libs.oauth.ServiceInfo
import service.txbitsUserService
import models.{ LogEvent, LogType, LogModel }

/**
 * A request that adds the User for the current call
 */
case class SecuredRequest[A](user: SocialUser, request: Request[A]) extends WrappedRequest(request)

/**
 * A request that adds the User for the current call
 */
case class RequestWithUser[A](user: Option[SocialUser], request: Request[A]) extends WrappedRequest(request)

/**
 * Provides the actions that can be used to protect controllers and retrieve the current user
 * if available.
 *
 * object MyController extends SecureSocial {
 *    def protectedAction = SecuredAction { implicit request =>
 *      Ok("Hello %s".format(request.user.displayName))
 *    }
 */
trait SecureSocial extends Controller {
  /**
   * A Forbidden response for ajax clients
   * @param request
   * @tparam A
   * @return
   */
  private def ajaxCallNotAuthenticated[A](implicit request: Request[A]): Result = {
    Unauthorized(Json.toJson(Map("error" -> "Credentials required"))).as(JSON)
  }

  /**
   * A secured action.  If there is no user in the session the request is redirected
   * to the login page
   */
  object SecuredAction extends SecuredActionBuilder[SecuredRequest[_]] {
    /**
     * Creates a secured action
     */
    def apply[A]() = new SecuredActionBuilder[A](false)

    /**
     * Creates a secured action
     *
     * @param ajaxCall a boolean indicating whether this is an ajax call or not
     */
    def apply[A](ajaxCall: Boolean) = new SecuredActionBuilder[A](ajaxCall)

    /**
     * Creates a secured action
     * @param authorize an Authorize object that checks if the user is authorized to invoke the action
     */
    //def apply[A](authorize: Authorization) = new SecuredActionBuilder[A](false, Some(authorize))

    /**
     * Creates a secured action
     * @param ajaxCall a boolean indicating whether this is an ajax call or not
     * @param authorize an Authorize object that checks if the user is authorized to invoke the action
     */
    //def apply[A](ajaxCall: Boolean, authorize: Authorization) = new SecuredActionBuilder[A](ajaxCall, Some(authorize))
  }

  /**
   * A builder for secured actions
   *
   * @param ajaxCall a boolean indicating whether this is an ajax call or not
   * @param authorize an Authorize object that checks if the user is authorized to invoke the action
   * @tparam A
   */
  class SecuredActionBuilder[A](ajaxCall: Boolean = false)
      extends ActionBuilder[({ type R[A] = SecuredRequest[A] })#R] {

    def invokeSecuredBlock[A](ajaxCall: Boolean, request: Request[A],
      block: SecuredRequest[A] => Future[Result]): Future[Result] =
      {
        implicit val req = request
        val result = for (
          authenticator <- SecureSocial.authenticatorFromRequest;
          uid <- authenticator.uid;
          user <- txbitsUserService.find(uid)
        ) yield {
          touch(authenticator)
          block(SecuredRequest(user, request))
        }

        result.getOrElse({
          if (Logger.isDebugEnabled) {
            Logger.debug("[securesocial] anonymous user trying to access : '%s'".format(request.uri))
          }
          val response = if (ajaxCall) {
            ajaxCallNotAuthenticated(request)
          } else {
            Redirect(controllers.routes.LoginPage.login().absoluteURL(UsernamePasswordProvider.sslEnabled))
              .flashing("error" -> Messages("auth.loginRequired"))
              .withSession(request2session + (SecureSocial.OriginalUrlKey -> request.uri)
              )
          }
          Future.successful(response) //TODO discard the cookie only if there was no authenticator or user found (not if the user is waiting for 2fa)
        })
      }

    def invokeBlock[A](request: Request[A], block: SecuredRequest[A] => Future[Result]) =
      invokeSecuredBlock(ajaxCall, request, block)
  }

  /**
   * An action that adds the current user in the request if it's available.
   */
  object UserAwareAction extends ActionBuilder[RequestWithUser] {
    def invokeBlock[A](request: Request[A],
      block: (RequestWithUser[A]) => Future[Result]): Future[Result] =
      {
        implicit val req = request
        val user = for (
          authenticator <- SecureSocial.authenticatorFromRequest;
          uid <- authenticator.uid;
          user <- txbitsUserService.find(uid) //TODO: this triggers on the login page too!
        ) yield {
          touch(authenticator)
          user
        }
        block(RequestWithUser(user, request))
      }
  }

  def touch(authenticator: Authenticator) {
    Authenticator.save(authenticator.touch)
  }
}

object SecureSocial {
  val OriginalUrlKey = "original-url"

  def authenticatorFromRequest(implicit request: RequestHeader): Option[Authenticator] = {
    val result = for {
      cookie <- request.cookies.get(Authenticator.cookieName)
      authenticator <- Authenticator.find(cookie.value)
    } yield {
      authenticator
    }

    result match {
      case Some(a) => {
        if (!a.isValid) {
          Authenticator.delete(a.id)
          globals.logModel.logEvent(LogEvent.fromRequest(a.uid, None, request, LogType.SessionExpired))
          None
        } else {
          Some(a)
        }
      }
      case None => None
    }
  }

  /**
   * Get the current logged in user.  This method can be used from public actions that need to
   * access the current user if there's any
   *
   * @param request
   * @tparam A
   * @return
   */
  def currentUser[A](implicit request: RequestHeader): Option[SocialUser] = {
    request match {
      case securedRequest: SecuredRequest[_] => Some(securedRequest.user)
      case userAware: RequestWithUser[_] => userAware.user
      case _ => for (
        authenticator <- authenticatorFromRequest;
        uid <- authenticator.uid;
        user <- txbitsUserService.find(uid)
      ) yield {
        user
      }
    }
  }

  /**
   * Saves the referer as original url in the session if it's not yet set.
   * @param result the result that maybe enhanced with an updated session
   * @return the result that's returned to the client
   */
  def withRefererAsOriginalUrl[A](result: Result)(implicit request: Request[A]): Result = {
    request.session.get(OriginalUrlKey) match {
      // If there's already an original url recorded we keep it: e.g. if s.o. goes to
      // login, switches to signup and goes back to login we want to keep the first referer
      case Some(_) => result
      case None => {
        request.headers.get(HeaderNames.REFERER).map { referer =>
          // we don't want to use the ful referer, as then we might redirect from https
          // back to http and loose our session. So let's get the path and query string only
          val idxFirstSlash = referer.indexOf("/", "https://".length())
          val refererUri = if (idxFirstSlash < 0) "/" else referer.substring(idxFirstSlash)
          result.withSession(
            request.session + (OriginalUrlKey -> refererUri))
        }.getOrElse(result)
      }
    }
  }

  val enableRefererAsOriginalUrl = {
    import play.api.Play
    Play.current.configuration.getBoolean("securesocial.enableRefererAsOriginalUrl").getOrElse(false)
  }
}
