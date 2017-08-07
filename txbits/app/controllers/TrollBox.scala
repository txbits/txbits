package controllers

import javax.inject.Inject

import play.api._
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.Play.current
import org.joda.time.DateTime
import play.api.i18n.MessagesApi
import play.api.i18n.{ Lang, MessagesApi, I18nSupport, Messages }
import models.TrollBoxMessage
import securesocial.core.SecureSocial
import securesocial.core.UsernamePasswordProvider

class TrollBox @Inject() (val messagesApi: MessagesApi) extends Controller with securesocial.core.SecureSocial with I18nSupport {
  def getMessages = Action { implicit request =>
    var messages = globals.trollBoxModel.messages()

    Ok(Json.toJson(messages))
  }

  def postMessage = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    try {
      val body = request.body
      (for (
        message <- (body \ "body").validate[String]
      ) yield {
        var messageObj = globals.trollBoxModel.buildMessage(message, request.user)
        globals.trollBoxModel.pushMessage(messageObj)
        Ok(Json.obj())
      }).getOrElse(
        BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoparseinput")))
      )
    } catch {
      case _: Throwable =>
        BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoparseinput")))
    }

  }

  def upvote = SecuredAction(ajaxCall = true)(parse.json) { implicit request =>
    try {
      val body = request.body
      (for (
        messageID <- (body \ "message_id").validate[Int]
      ) yield {
        globals.trollBoxModel.upvote(messageID, request.user.email)
        Ok(Json.obj())
      }).getOrElse(
        BadRequest(Json.obj("message" -> "Didn't parse this right"))
      )
    } catch {
      case _: Throwable =>
        BadRequest(Json.obj("message" -> Messages("messages.api.error.failedtoparseinput")))
    }
  }
}

