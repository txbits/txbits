// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package helpers

import org.specs2.execute.AsResult
import play.api.test.{ WithApplication, FakeApplication }

abstract class WithCleanTestDbApplicationReal extends WithCleanDbApplication()(FakeApplication(
  additionalConfiguration = Map(
    "db.default.url" -> "postgres://user:password@localhost/txbits_test",
    "fakeexchange" -> false,
    "litecoin.actor.enabled" -> false,
    "bitcoin.actor.enabled" -> false
  ))) {
}
