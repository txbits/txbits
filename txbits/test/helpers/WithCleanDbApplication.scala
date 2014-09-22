// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package helpers

import org.specs2.execute.AsResult
import play.api.test.{ WithApplication, FakeApplication }

abstract class WithCleanDbApplication(implicit implicitApp: play.api.test.FakeApplication = FakeApplication()) extends WithApplication(implicitApp) {
  override def around[T: AsResult](t: => T) = super.around {
    setupData()
    t
  }

  def setupData() {
    globals.engineModel.clean()
    globals.engineModel.testSetup()
  }
}
