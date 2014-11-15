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

import play.api.Play
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{ AnyContent, Controller, Result }
import securesocial.core.{ SecuredRequest, _ }
import securesocial.core.providers.utils.{ Mailer, PasswordValidator }
import service.txbitsUserService

/**
 * A controller to provide password change functionality
 */
object PasswordChange extends Controller with SecureSocial {
  val CurrentPassword = "currentPassword"
  val InvalidPasswordMessage = "securesocial.passwordChange.invalidPassword"
  val Password = "password"
  val Password1 = "password1"
  val Password2 = "password2"
  val Success = "success"
  val OkMessage = "securesocial.passwordChange.ok"

  /**
   * The property that specifies the page the user is redirected to after changing the password.
   */
  val onPasswordChangeGoTo = "securesocial.onPasswordChangeGoTo"

  /** The redirect target of the handlePasswordChange action. */
  def onHandlePasswordChangeGoTo = Play.current.configuration.getString(onPasswordChangeGoTo).getOrElse(
    controllers.routes.PasswordChange.page().url
  )

  case class ChangeInfo(currentPassword: String, password: String)

  def checkCurrentPassword[A](currentPassword: String)(implicit request: SecuredRequest[A]): Boolean = {
    txbitsUserService.findByEmailAndPassword(request.user.email, currentPassword).isDefined
  }

  private def execute[A](f: (SecuredRequest[A], Form[ChangeInfo]) => Result)(implicit request: SecuredRequest[A]): Result = {
    val form = Form[ChangeInfo](
      mapping(
        CurrentPassword -> nonEmptyText.verifying(
          Messages(InvalidPasswordMessage), checkCurrentPassword(_)),
        Password ->
          tuple(
            Password1 -> nonEmptyText.verifying(PasswordValidator.validator),
            Password2 -> nonEmptyText
          ).verifying(Messages(Registration.PasswordsDoNotMatch), passwords => passwords._1 == passwords._2)

      )((currentPassword, password) => ChangeInfo(currentPassword, password._1))((changeInfo: ChangeInfo) => Some("", ("", "")))
    )

    f(request, form)
  }

  def page = SecuredAction { implicit request =>
    execute { (request: SecuredRequest[AnyContent], form: Form[ChangeInfo]) =>
      Ok(SecureSocialTemplates.getPasswordChangePage(request, form))
    }
  }

  def handlePasswordChange = SecuredAction { implicit request =>
    execute { (request: SecuredRequest[AnyContent], form: Form[ChangeInfo]) =>
      form.bindFromRequest()(request).fold(
        errors => BadRequest(SecureSocialTemplates.getPasswordChangePage(request, errors)),
        info => {
          import scala.language.reflectiveCalls
          // This never actually fails because we already checked that the password is valid in the validators
          globals.userModel.userChangePass(request.user.id, info.currentPassword, info.password)
          Mailer.sendPasswordChangedNotice(request.user.email)(request)
          Redirect(onHandlePasswordChangeGoTo).flashing(Success -> Messages(OkMessage))
        }
      )
    }
  }
}
