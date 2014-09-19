package helpers

import play.api.test.FakeApplication

abstract class WithCleanTestDbApplication extends WithCleanDbApplication()(FakeApplication(
  additionalConfiguration = Map(
    "db.default.url" -> "postgres://user:password@localhost/txbits_test",
    "wallet.litecoin.enabled" -> false,
    "wallet.bitcoin.enabled" -> false
  ))) {
}