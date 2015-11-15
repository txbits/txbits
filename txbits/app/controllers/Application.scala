// TxBits - An open source Bitcoin and crypto currency exchange
// Copyright (C) 2014-2015  Viktor Stanchev & Kirk Zathey
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package controllers

import javax.inject.Inject

import play.api.mvc._
import play.api.i18n.{ I18nSupport, Lang }
import play.api.Play.current
import play.api.i18n.MessagesApi
import play.i18n.Langs
import scala.language.postfixOps

class Application @Inject() (val messagesApi: MessagesApi) extends Controller with securesocial.core.SecureSocial with I18nSupport {

  def index = UserAwareAction { implicit request =>
    Ok(views.html.content.index(request.user.isDefined))
  }

  def dashboard = SecuredAction { implicit request =>
    Ok(views.html.exchange.dashboard(request.user))
  }

  def account = SecuredAction { implicit request =>
    Ok(views.html.exchange.account(request.user))
  }

  def depositwithdraw = SecuredAction { implicit request =>
    Ok(views.html.exchange.depositwithdraw(request.user))
  }

  def exchange = SecuredAction { implicit request =>
    Ok(views.html.exchange.trade(request.user))
  }

  def history = SecuredAction { implicit request =>
    Ok(views.html.exchange.history(request.user))
  }

  def chlang(lang: String) = UserAwareAction { implicit request =>
    Redirect("/").withLang(Lang.get(lang).getOrElse(Lang.defaultLang))
  }
}