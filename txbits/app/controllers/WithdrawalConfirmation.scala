package controllers

import play.api.mvc.Action
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.i18n.Messages

object WithdrawalConfirmation extends Controller with securesocial.core.SecureSocial {

  def confirm(idStr: String, token: String) = Action { implicit request =>
    val id = idStr.toLong
    if (globals.engineModel.confirmWithdrawal(id, token)) {
      Redirect(controllers.routes.Application.index()).flashing("success" -> Messages("withdrawal.confirm.success"))
    } else {
      Redirect(controllers.routes.Application.index()).flashing("error" -> Messages("withdrawal.confirm.fail"))
    }
  }
  def reject(idStr: String, token: String) = Action { implicit request =>
    val id = idStr.toLong
    if (globals.engineModel.rejectWithdrawal(id, token)) {
      Redirect(controllers.routes.Application.index()).flashing("success" -> Messages("withdrawal.reject.success"))
    } else {
      Redirect(controllers.routes.Application.index()).flashing("error" -> Messages("withdrawal.reject.fail"))
    }
  }
}
