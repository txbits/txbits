// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package service

import play.api.Play
import views.html.helper

object TOTPUrl {
  def totpSecretToUrl(email: String, secret: TOTPSecret) = {
    val applicationName = Play.current.configuration.getString("application.name").get +
      (if (globals.fakeExchange) " (testnet)" else "")
    "otpauth://totp/%s?secret=%s&issuer=%s".format(
      helper.urlEncode("%s: %s".format(applicationName, email)).replace("+", "%20"),
      secret.toBase32,
      helper.urlEncode(applicationName))
  }
}
