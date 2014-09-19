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