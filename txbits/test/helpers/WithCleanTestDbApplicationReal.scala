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

package helpers

import org.specs2.execute.AsResult
import play.api.test.{ WithApplication, FakeApplication }

abstract class WithCleanTestDbApplicationReal extends WithCleanDbApplication()(FakeApplication(
  additionalConfiguration = Map(
    "db.default.url" -> "postgres://user:password@localhost/txbits_test",
    "db.wallet.url" -> "postgres://user:password@localhost/txbits_test",
    "db.trust.url" -> "postgres://user:password@localhost/txbits_test",
    "fakeexchange" -> false,
    "litecoin.actor.enabled" -> false,
    "bitcoin.actor.enabled" -> false
  ))) {
}
