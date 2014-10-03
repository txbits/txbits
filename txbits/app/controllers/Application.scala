// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package controllers

import play.api.mvc._
import play.api.i18n.Lang
import play.api.Play.current
import scala.language.postfixOps

object Application extends Controller with securesocial.core.SecureSocial {

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