// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package controllers

import org.joda.time.DateTime
import play.api.mvc.{ Request, AnyContent }

object Util {

  def parse_pagination_params(implicit request: Request[AnyContent]) = {
    val before = request.queryString.getOrElse("before", List()).headOption.map({ a => new DateTime(java.lang.Long.parseLong(a)) })
    val limit = request.queryString.getOrElse("limit", List()).headOption.map(Integer.parseInt)
    (before, limit)
  }
}
