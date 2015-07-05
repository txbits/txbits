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

package service

import play.api.Play
import views.html.helper

object TOTPUrl {
  def totpSecretToUrl(email: String, secret: TOTPSecret) = {
    val applicationName = Play.current.configuration.getString("application.name").get +
      (if (Play.current.configuration.getBoolean("fakeexchange").get) " (testnet)" else "")
    "otpauth://totp/%s?secret=%s&issuer=%s".format(
      helper.urlEncode("%s: %s".format(applicationName, email)).replace("+", "%20"),
      secret.toBase32,
      helper.urlEncode(applicationName))
  }
}
