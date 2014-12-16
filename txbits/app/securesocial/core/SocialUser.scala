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

import play.api.libs.json.{ JsValue, Json, JsObject, Writes }

/**
 * An implementation of Identity.  Used by SecureSocial to gather user information when users sign up and/or sign in.
 */
case class SocialUser(id: Long, email: String, verification: Int, onMailingList: Boolean, TFAEnabled: Boolean = false, pgp: Option[String] = None)

object SocialUser {
  implicit def writes = new Writes[SocialUser] {
    def writes(u: SocialUser): JsValue = {
      // include everything except the id
      Json.obj("email" -> u.email, "verification" -> u.verification, "onMailingList" -> u.onMailingList,
        "TFAEnabled" -> u.TFAEnabled, "pgp" -> u.pgp)
    }
  }
}
