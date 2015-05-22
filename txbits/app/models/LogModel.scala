// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package models

import play.api.db.DB
import play.api.Play.current
import anorm._
import anorm.SqlParser._
import play.api.Play
import java.sql.Timestamp
import org.joda.time.DateTime
import models.LogType.LogType
import play.api.libs.json.{ JsString, JsValue, Writes, Json }
import play.api.mvc.{ RequestHeader, AnyContent, Request }
import service.sql.frontend

object LogType extends Enumeration {
  type LogType = Value
  val LoginPartialSuccess = Value("login_partial_success")
  val LoginSuccess = Value("login_success")
  val SignupSuccess = Value("signup_success")
  val LoginFailure = Value("login_failure")
  val Logout = Value("logout")
  val SessionExpired = Value("session_expired")
  val Other = Value("other")
  implicit def logTypeWrites = new Writes[LogType] {
    def writes(v: LogType): JsValue = JsString(v.toString)
  }
  /*implicit def logTypeRead = new Reads[LogType] {
    def reads(json: JsValue): JsResult[LogType] = json match {
      case JsLogModel.mkAuthLog(String(v) => LogType.withName(v) match {
        case Some(a:LogType) => JsSuccess(a)
        case _ => JsSuccess(LogType.Other)
      }
      case _ => JsError("String value expected")
    }
  }*/
}

case class LogEvent(uid: Option[Long], email: Option[String], ip: Option[String], browser_headers: Option[String], browser_id: Option[String], ssl_info: Option[String], created: Option[DateTime], typ: LogType)

object LogEvent {
  implicit val logEventWrites = Json.writes[LogEvent]
  def fromRequest(uid: Option[Long], email: Option[String], request: RequestHeader, typ: LogType) = {
    LogEvent(uid, email, Some(LogModel.ipFromRequest(request)), Some(LogModel.headersFromRequest(request)), None, None, None, typ)
  }
}
case class LoginEvent(email: Option[String], ip: Option[String], created: Option[DateTime], typ: LogType)

object LoginEvent {
  implicit val writes = Json.writes[LoginEvent]
}

class LogModel(val db: String = "default") {

  def logEvent(logEvent: LogEvent) = DB.withConnection(db) { implicit c =>
    frontend.newLog.on(
      'user_id -> logEvent.uid,
      'email -> logEvent.email,
      'ip -> logEvent.ip,
      'browser_headers -> logEvent.browser_headers,
      'browser_id -> logEvent.browser_id,
      'ssl_info -> logEvent.ssl_info,
      'type -> logEvent.typ.toString
    ).execute()
  }

  def getLoginEvents(uid: Long, before: Option[DateTime] = None, limit: Option[Int] = None) = DB.withConnection(db) { implicit c =>
    frontend.loginLog.on(
      'user_id -> uid,
      'before -> before,
      'limit -> limit
    )().map(row => LoginEvent(
        row[Option[String]]("email"),
        row[Option[String]]("ip"),
        Some(row[DateTime]("created")),
        LogType.withName(row[Option[String]]("type").getOrElse("other")))
      ).toList
  }

}

object LogModel {

  def ipFromRequest(request: RequestHeader) = {
    if (Play.current.configuration.getBoolean("reverseproxy").get) {
      // remoteAddress will always be 127.0.0.1 when running behind a reverse proxy
      // Must read the user's IP address from the X-Forwarded-For header
      Json.toJson(request.headers.toMap).\\("X-Forwarded-For").lastOption.toString
    } else {
      request.remoteAddress.takeWhile(c => c != '%')
    }
  }

  def headersFromRequest(request: RequestHeader) = {
    Json.toJson(request.headers.toMap).toString()
  }

  def isIpv4(s: String) = {
    s.contains('.')
  }

  def ip4StrToInt(s: String) = {
    val parts = s.split("\\.").map(_.toInt)
    parts.slice(1, 4).foldLeft(parts(0))((v, w) => (v << 8) + w)
  }

  def ip6StrToBigDecimal(s: String) = {
    val parts = s.takeWhile(c => c != '%').split(":").map(_.toInt)
    parts.slice(1, 7).foldLeft(BigDecimal(parts(0)))((v, w) => (v * (2 ^ 8)) + w)
  }

  def ip4IntToStr(ip: Int) = (0 to 3).map { i => (ip >> ((3 - i) * 8)) & 255 }.mkString(".")

  def ip6BigDecimalToStr(ip: BigDecimal) = (0 to 3).map(i => "%02x".format((ip / (2 ^ ((3 - i) * 8))).toInt & 256)).mkString(".")

  def ipStrToNum(s: String) = {
    if (isIpv4(s)) {
      ip4StrToInt(s)
    } else {
      ip6StrToBigDecimal(s)
    }
  }
}