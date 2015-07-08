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
package securesocial.core.providers.utils

import securesocial.core.SocialUser
import play.api.{ Play, Logger }
import Play.current
import play.api.libs.concurrent.Akka
import play.api.mvc.RequestHeader
import play.api.i18n.Messages
import play.twirl.api.{ Html, Txt }
import org.apache.commons.mail.{ DefaultAuthenticator, SimpleEmail, MultiPartEmail, EmailAttachment }
import java.io.File
import javax.mail.internet.InternetAddress
import service.PGP

/**
 * A helper class to send email notifications
 */
object Mailer {
  val fromAddress = current.configuration.getString("smtp.from").get
  val WithdrawalConfirmSubject = "mails.sendWithdrawalConfirmEmail.subject"
  val AlreadyRegisteredSubject = "mails.sendAlreadyRegisteredEmail.subject"
  val SignUpEmailSubject = "mails.sendSignUpEmail.subject"
  val WelcomeEmailSubject = "mails.welcomeEmail.subject"
  val PasswordResetSubject = "mails.passwordResetEmail.subject"
  val UnknownEmailNoticeSubject = "mails.unknownEmail.subject"
  val PasswordResetOkSubject = "mails.passwordResetOk.subject"

  def sendWithdrawalConfirmEmail(email: String, amount: String, currency: String, destination: String, id: Long, token: String, pgp: Option[String])(implicit messages: Messages) {
    val url_confirm = "%s%s/%s".format(current.configuration.getString("url.withdrawal_confirm").getOrElse("http://localhost:9000/withdrawal_confirm/"), id, token)
    val url_reject = "%s%s/%s".format(current.configuration.getString("url.withdrawal_reject").getOrElse("http://localhost:9000/withdrawal_reject/"), id, token)
    val txtAndHtml = (Some(views.txt.auth.mails.withdrawalConfirmEmail(email, amount, currency, destination, id, token, url_confirm, url_reject)), None)
    sendEmail(Messages(WithdrawalConfirmSubject), email, txtAndHtml, pgp)
  }

  def sendRefillWalletEmail(email: String, currency: String, nodeId: Int, balance: BigDecimal, balanceTarget: BigDecimal)(implicit messages: Messages) {
    val txtAndHtml = (Some(views.txt.auth.mails.refillWalletEmail(email, currency, nodeId, balance, balanceTarget)), None)
    sendEmail(s"Refill $currency wallet $nodeId", email, txtAndHtml)
  }

  def sendAlreadyRegisteredEmail(email: String, pgp: Option[String])(implicit messages: Messages) {
    val url = current.configuration.getString("url.passwordreset").getOrElse("http://localhost:9000/reset/")
    val txtAndHtml = (Some(views.txt.auth.mails.alreadyRegisteredEmail(email, url)), None)
    sendEmail(Messages(AlreadyRegisteredSubject), email, txtAndHtml, pgp)
  }

  def sendSignUpEmail(to: String, token: String)(implicit messages: Messages) {
    val url = current.configuration.getString("url.signup").getOrElse("http://localhost:9000/signup/") + token
    val txtAndHtml = (Some(views.txt.auth.mails.signUpEmail(token, url)), None)
    sendEmail(Messages(SignUpEmailSubject), to, txtAndHtml)
  }

  def sendWelcomeEmail(user: SocialUser)(implicit request: RequestHeader, messages: Messages) {
    val txtAndHtml = (Some(views.txt.auth.mails.welcomeEmail(user)), None)
    sendEmail(Messages(WelcomeEmailSubject), user.email, txtAndHtml, user.pgp)
  }

  def sendPasswordResetEmail(email: String, token: String, pgp: Option[String])(implicit messages: Messages) {
    val url = current.configuration.getString("url.passwordreset").getOrElse("http://localhost:9000/reset/") + token
    val txtAndHtml = (Some(views.txt.auth.mails.passwordResetEmail(email, url)), None)
    sendEmail(Messages(PasswordResetSubject), email, txtAndHtml, pgp)
  }

  def sendPasswordChangedNotice(email: String, pgp: Option[String])(implicit request: RequestHeader, messages: Messages) {
    val txtAndHtml = (Some(views.txt.auth.mails.passwordChangedNotice(email)), None)
    sendEmail(Messages(PasswordResetOkSubject), email, txtAndHtml, pgp)
  }

  private def sendEmail(subject: String, recipient: String, body: (Option[Txt], Option[Html]), pgp: Option[String] = None) {
    import com.typesafe.plugin._
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits._

    if (Logger.isDebugEnabled) {
      Logger.debug("[securesocial] sending email to %s".format(recipient))
    }
    val strBody = pgp match {
      case Some(pgp_key) => PGP.simple_encrypt(pgp_key, body._1.get.toString())
      case None => body._1.get.toString()
    }

    Akka.system.scheduler.scheduleOnce(1.seconds) {
      val smtpHost = Play.current.configuration.getString("smtp.host").getOrElse(throw new RuntimeException("smtp.host needs to be set in application.conf in order to use this plugin (or set smtp.mock to true)"))
      val smtpPort = Play.current.configuration.getInt("smtp.port").getOrElse(25)
      val smtpSsl = Play.current.configuration.getBoolean("smtp.ssl").getOrElse(false)
      val smtpUser = Play.current.configuration.getString("smtp.user").get
      val smtpPassword = Play.current.configuration.getString("smtp.password").get
      val smtpLocalhost = current.configuration.getString("smtp.localhost").get
      val email = new SimpleEmail()
      email.setMsg(strBody)
      email.setHostName(smtpLocalhost)
      //TODO: move this somewhere better
      System.setProperty("mail.smtp.localhost", current.configuration.getString("smtp.localhost").get)
      email.setCharset("utf-8")
      email.setSubject(subject)
      setAddress(fromAddress) { (address, name) => email.setFrom(address, name) }
      email.addTo(recipient, null)
      email.setHostName(smtpHost)
      email.setSmtpPort(smtpPort)
      email.setSSLOnConnect(smtpSsl)
      email.setAuthentication(smtpUser, smtpPassword)
      try {
        email.send
      } catch {
        case ex: Throwable => {
          // important: Print the bodies of emails in logs only if dealing with fake money
          if (Play.current.configuration.getBoolean("fakeexchange").get) {
            Logger.debug("Failed to send email to %s, email body:\n%s".format(recipient, strBody))
          }
          throw ex
        }
      }
    }
  }

  /**
   * Extracts an email address from the given string and passes to the enclosed method.
   * https://github.com/typesafehub/play-plugins/blob/master/mailer/src/main/scala/com/typesafe/plugin/MailerPlugin.scala
   *
   * @param emailAddress
   * @param setter
   */
  private def setAddress(emailAddress: String)(setter: (String, String) => Unit) = {

    if (emailAddress != null) {
      try {
        val iAddress = new InternetAddress(emailAddress)
        val address = iAddress.getAddress
        val name = iAddress.getPersonal

        setter(address, name)
      } catch {
        case e: Exception =>
          setter(emailAddress, null)
      }
    }
  }

  // XXX: currently not used
  def sendEmailWithFile(subject: String, recipient: String, body: String, attachment: EmailAttachment) {
    import com.typesafe.plugin._
    import scala.concurrent.duration._
    import play.api.libs.concurrent.Execution.Implicits._

    if (Logger.isDebugEnabled) {
      Logger.debug("[securesocial] sending email to %s".format(recipient))
      Logger.debug("[securesocial] mail = [%s]".format(body))
    }

    Akka.system.scheduler.scheduleOnce(1.seconds) {
      // we can't use the plugin easily with multipart emails
      val email = new MultiPartEmail
      email.setHostName(current.configuration.getString("smtp.host").get)
      //TODO: move this somewhere better
      System.setProperty("mail.smtp.localhost", current.configuration.getString("smtp.localhost").get)
      email.attach(attachment)
      email.setSubject(subject)
      email.addTo(recipient)
      email.setBoolHasAttachments(true)
      email.setSmtpPort(current.configuration.getInt("smtp.port").getOrElse(25))
      email.setSSLOnConnect(current.configuration.getBoolean("smtp.ssl").get)
      email.setAuthentication(current.configuration.getString("smtp.user").get, current.configuration.getString("smtp.password").get)
      setAddress(fromAddress) { (address, name) => email.setFrom(address, name) }
      email.setMsg(body)
      email.send()
      new File(attachment.getPath).delete()
    }
  }
}
