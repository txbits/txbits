/**
 * Copyright 2013-2014 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
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

import java.security.SecureRandom
import org.joda.time.DateTime
import play.api.libs.Codecs
import play.api.{ Play, Application, Plugin }
import com.typesafe.plugin._
import Play.current
import play.api.cache.Cache
import play.api.mvc.{ DiscardingCookie, Cookie }
import org.apache.commons.codec.binary.Base64.encodeBase64

/**
 * An authenticator tracks an authenticated user.
 *
 * @param id The authenticator id
 * @param uid The user id
 * @param creationDate The creation timestamp
 * @param lastUsed The last used timestamp
 * @param expirationDate The expiration time
 */
case class Authenticator(id: String, uid: Option[Long], creationDate: DateTime,
    lastUsed: DateTime, expirationDate: DateTime, totpSecret: Option[String], email: String) {

  /**
   * Creates a cookie representing this authenticator
   *
   * @return a cookie instance
   */
  def toCookie: Cookie = {
    import Authenticator._
    Cookie(
      cookieName,
      id,
      if (makeTransient) Transient else Some(absoluteTimeoutInSeconds),
      cookiePath,
      cookieDomain,
      secure = cookieSecure,
      httpOnly = cookieHttpOnly
    )
  }

  /**
   * Checks if the authenticator has expired. This is an absolute timeout since the creation of
   * the authenticator
   *
   * @return true if the authenticator has expired, false otherwise.
   */
  def expired: Boolean = expirationDate.isBeforeNow

  /**
   * Checks if the time elapsed since the last time the authenticator was used is longer than
   * the maximum idle timeout specified in the properties.
   *
   * @return true if the authenticator timed out, false otherwise.
   */
  def timedOut: Boolean = lastUsed.plusMinutes(Authenticator.idleTimeout).isBeforeNow

  def isValid: Boolean = !expired && !timedOut

  /**
   * Updates the last used timestap (note that this does not save it in the store)
   *
   * @return A new authenticator instance with the new timestamp.
   */
  def touch: Authenticator = this.copy(lastUsed = DateTime.now())
  def complete2fa(uid: Long): Authenticator = this.copy(uid = Some(uid), totpSecret = None)
}

/**
 * The default id generator
 */
object IdGenerator {
  //todo: this needs improvement, several threads will wait for the synchronized block in SecureRandom.
  // I will probably need a pool of SecureRandom instances.
  val random = new SecureRandom()
  // memcache can handle only 250 character keys. 32 bytes (256 bits) is 44 characters in Base64.
  val DefaultSizeInBytes = 32
  val IdLengthKey = "securesocial.idLengthInBytes"
  val IdSizeInBytes = current.configuration.getInt(IdLengthKey).getOrElse(DefaultSizeInBytes)

  /**
   * Generates a new id using SecureRandom
   *
   * @return the generated id
   */
  def generate: String = {
    val bytes = new Array[Byte](IdSizeInBytes)
    random.nextBytes(bytes)
    new String(encodeBase64(bytes))
  }
}

/**
 * A default implementation of the AuthenticationStore that uses the Play cache.
 * Note: if deploying to multiple nodes the caches will need to synchronize.
 */
object AuthenticatorStore {
  val IdPrefix = "session."

  def save(authenticator: Authenticator) {
    Cache.set(IdPrefix + authenticator.id, authenticator, Authenticator.absoluteTimeoutInSeconds)
  }
  def find(id: String): Option[Authenticator] = {
    Cache.getAs[Authenticator](IdPrefix + id)
  }
  def delete(id: String) {
    Cache.remove(IdPrefix + id)
  }
}

object Authenticator {
  // property keys
  val CookieNameKey = "securesocial.cookie.name"
  val CookiePathKey = "securesocial.cookie.path"
  val CookieDomainKey = "securesocial.cookie.domain"
  val CookieHttpOnlyKey = "securesocial.cookie.httpOnly"
  val ApplicationContext = "application.context"
  val IdleTimeoutKey = "securesocial.cookie.idleTimeoutInMinutes"
  val AbsoluteTimeoutKey = "securesocial.cookie.absoluteTimeoutInMinutes"
  val TransientKey = "securesocial.cookie.makeTransient"

  // default values
  val DefaultCookieName = "id"
  val DefaultCookiePath = "/"
  val DefaultCookieHttpOnly = true
  val Transient = None
  val DefaultIdleTimeout = 30
  val DefaultAbsoluteTimeout = 12 * 60

  lazy val cookieName = Play.application.configuration.getString(CookieNameKey).getOrElse(DefaultCookieName)
  lazy val cookiePath = Play.application.configuration.getString(CookiePathKey).getOrElse(
    Play.configuration.getString(ApplicationContext).getOrElse(DefaultCookiePath)
  )
  lazy val cookieDomain = Play.application.configuration.getString(CookieDomainKey)
  lazy val cookieSecure = UsernamePasswordProvider.sslEnabled
  lazy val cookieHttpOnly = Play.application.configuration.getBoolean(CookieHttpOnlyKey).getOrElse(DefaultCookieHttpOnly)
  lazy val idleTimeout = Play.application.configuration.getInt(IdleTimeoutKey).getOrElse(DefaultIdleTimeout)
  lazy val absoluteTimeout = Play.application.configuration.getInt(AbsoluteTimeoutKey).getOrElse(DefaultAbsoluteTimeout)
  lazy val absoluteTimeoutInSeconds = absoluteTimeout * 60
  lazy val makeTransient = Play.application.configuration.getBoolean(TransientKey).getOrElse(true)

  val discardingCookie: DiscardingCookie = {
    DiscardingCookie(cookieName, cookiePath, cookieDomain, cookieSecure)
  }

  /**
   * Creates a new authenticator id for the specified user
   *
   * @param uid the id of the user if no two factor auth or None
   * @param totpSecret the secret to complete two factor auth or None
   * @return an authenticator or error if there was a problem creating it
   */
  def create(uid: Option[Long], totpSecret: Option[String], email: String): Authenticator = {
    val id = IdGenerator.generate
    val now = DateTime.now()
    val expirationDate = now.plusMinutes(absoluteTimeout)
    val authenticator = Authenticator(id, uid, now, now, expirationDate, totpSecret, email)
    AuthenticatorStore.save(authenticator)
    authenticator
  }

  /**
   * Saves or updates the authenticator in the store
   *
   * @param authenticator the authenticator
   * @return Error if there was a problem saving the authenticator or Unit if all went ok
   */
  def save(authenticator: Authenticator) {
    AuthenticatorStore.save(authenticator)
  }
  /**
   * Finds an authenticator by id
   *
   * @param id the authenticator id
   * @return Error if there was a problem finding the authenticator or an optional authenticator if all went ok
   */
  def find(id: String): Option[Authenticator] = {
    AuthenticatorStore.find(id)
  }

  /**
   * Deletes an authenticator
   *
   * @param id the authenticator id
   * @return Error if there was a problem deleting the authenticator or Unit if all went ok
   */
  def delete(id: String) {
    AuthenticatorStore.delete(id)
  }
}
