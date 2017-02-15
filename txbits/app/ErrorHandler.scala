import javax.inject.Inject

import play.api.http.HttpErrorHandler
import play.api.i18n.{ MessagesApi, I18nSupport }
import play.api.libs.json.Json
import play.api.mvc._
import play.api.mvc.Results._
import play.mvc.Http
import scala.concurrent._

class ErrorHandler @Inject() (val messagesApi: MessagesApi) extends HttpErrorHandler with I18nSupport {

  def onClientError(request: RequestHeader, statusCode: Int, message: String) = {
    implicit val r = request
    Future.successful((request.contentType.getOrElse("n/a"), statusCode) match {
      case ("application/json", Http.Status.BAD_REQUEST) =>
        BadRequest(Json.obj("error" -> ("Bad Request: " + message)))
      case ("application/json", Http.Status.NOT_FOUND) =>
        NotFound(Json.obj("error" -> ("Not found: " + request.path)))
      case ("application/json", _) =>
        Status(statusCode)(Json.obj("error" -> ("Client error: " + message)))

      case (_, Http.Status.BAD_REQUEST) =>
        BadRequest(views.html.meta.badRequest(message))
      case (_, Http.Status.NOT_FOUND) =>
        NotFound(views.html.meta.notFound(request.path))
      case (_, _) =>
        Status(statusCode)(views.html.meta.clientError(message))
    })
  }

  def onServerError(request: RequestHeader, exception: Throwable) = {
    implicit val r = request
    Future.successful(request.contentType.getOrElse("n/a") match {
      case "application/json" =>
        InternalServerError(Json.obj("error" -> ("Internal Error: " + exception)))
      case _ =>
        InternalServerError(views.html.meta.error(exception))
    })
  }
}