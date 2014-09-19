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