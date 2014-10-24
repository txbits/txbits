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

import java.sql.Timestamp

import globals._
import play.api.Play
import play.api.libs.json.{ Json, JsValue }
import service.{ TOTPSecret, TOTPAuthenticator }

/**
 * A trait to define Authorization objects that let you hook
 * an authorization implementation in SecuredActions
 *
 */
trait Authorization {
  /**
   * Checks whether the user is authorized to execute an action or not.
   *
   * @param user
   * @return
   */
  def isAuthorized[A](request: SecuredRequest[A], authenticator: Authenticator): Boolean
}

object Authorization2fa extends Authorization {
  def verifyTokenForUser(user: BigDecimal, token: String, secret: String) = {
    if (TOTPAuthenticator.pinMatchesSecret(token, TOTPSecret(secret))) {
      if (!userModel.TOTPTokenIsBlacklisted(user, token)) {
        val blacklistDuration: Long = Play.current.configuration.getLong("TOTPTokenBlacklistDurationMs").getOrElse(30 * 7 * 1000)
        userModel.blacklistTOTPToken(user, token, new Timestamp(System.currentTimeMillis() + blacklistDuration))
        true
      } else {
        false
      }
    } else {
      false
    }
  }
  // We probably have to also pass the request into here so we can pick up the 2fa token from it
  // (is this before the json parser or after?) Maybe we can pass the 2fa token in a header :)
  def isAuthorized[A](request: SecuredRequest[A], authenticator: Authenticator): Boolean = {
    if (request.user.TFAType == None) return true

    // if 2fa for withdrawal is disabled and we are withdrawing, don't do the check
    if (!request.user.TFAWithdrawal && request.request.path == controllers.API.routes.APIv1.withdraw().url) {
      true
    } else {
      request.body match {
        case body: JsValue =>
          (for (
            code <- (body \ "tfa_code").validate[String]
          ) yield {
            verifyTokenForUser(request.user.id, code, request.user.TFASecret.get)
          }).getOrElse(
            false
          )
        case _ => throw new Exception("Two factor authentication not implemented for this request body type.")
      }
    }
  }
}