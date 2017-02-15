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

import org.joda.time.DateTime
import play.api.mvc.{ Request, AnyContent }

object Util {

  def parse_pagination_params(implicit request: Request[AnyContent]) = {
    val before = request.queryString.getOrElse("before", List()).headOption.map({ a => new DateTime(java.lang.Long.parseLong(a)) })
    val limit = request.queryString.getOrElse("limit", List()).headOption.map(Integer.parseInt)
    val lastId = request.queryString.getOrElse("last_id", List()).headOption.map(java.lang.Long.parseLong)
    (before, limit, lastId)
  }
}
