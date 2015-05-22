// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package test

import org.specs2.mutable._
import akka.testkit.TestActorRef
import play.libs.Akka
import akka.pattern.ask
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.{ FakeApplication, WithApplication }
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.Helpers.inMemoryDatabase
import org.specs2.mutable._

import play.api.test._

import org.specs2.mock._
import models.TradeHistory
import org.postgresql.util.PSQLException
import play.api.test.WithApplication
import org.specs2.execute.AsResult
import org.joda.time.DateTime
import helpers._
import controllers.IAPI.CryptoAddress

@RunWith(classOf[JUnitRunner])
class EngineModelSpec extends Specification with Mockito {

  val fee = globals.metaModel.tradeFees

  "Engine" should {
    // the application is used for its database connection

    "tell us the balance" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", false).get
      val result = globals.engineModel.balance(Some(uid), None)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap
    }

    "be able to add fake balance on fake exchange" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", false).get
      globals.userModel.addFakeMoney(uid, "LTC", 1)

      val result = globals.engineModel.balance(Some(uid), None)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("LTC", (1, 0))
    }

    "not be able to add fake balance on real exchange" in new WithCleanTestDbApplicationReal {
      val uid = globals.userModel.create("test@test.test", "", false).get
      globals.userModel.addFakeMoney(uid, "LTC", 1)

      val result = globals.engineModel.balance(Some(uid), None)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap
    }

    "not be able to remove fake balance on real exchange" in new WithCleanTestDbApplicationReal {
      val uid = globals.userModel.create("test@test.test", "", false).get
      globals.userModel.subtractFakeMoney(uid, "LTC", 1)

      val result = globals.engineModel.balance(Some(uid), None)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap
    }

    "no money = no bid" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", false).get

      globals.engineModel.askBid(Some(uid), None, "LTC", "USD", 1, 1, true) must beFalse
    }

    "be able to place a bid" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", false).get

      globals.userModel.addFakeMoney(uid, "USD", 1)
      globals.engineModel.askBid(Some(uid), None, "LTC", "USD", 1, 1, true)

      val result = globals.engineModel.balance(Some(uid), None)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("USD", (1, 1))
    }

    "be able to place an ask" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", false).get
      globals.userModel.addFakeMoney(uid, "LTC", 1)
      globals.engineModel.askBid(Some(uid), None, "LTC", "USD", 1, 1, false)

      val result = globals.engineModel.balance(Some(uid), None)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("LTC", (1, 1))
    }

    "be able to make a match" in new WithCleanTestDbApplication {
      val asker = globals.userModel.create("test@test.test", "", false).get
      val bidder = globals.userModel.create("test2@test.test", "", false).get

      globals.userModel.addFakeMoney(asker, "LTC", 1)
      globals.userModel.addFakeMoney(bidder, "USD", 1)

      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 1, 1, false)
      globals.engineModel.askBid(Some(bidder), None, "LTC", "USD", 1, 1, true)

      val asker_res = globals.engineModel.balance(Some(asker), None)
      val bidder_res = globals.engineModel.balance(Some(bidder), None)
      asker_res should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("USD", (1 - fee, 0))
      bidder_res should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("LTC", (1 - fee, 0))
    }

    "be able to make a match of one and a half asks" in new WithCleanTestDbApplication {
      val asker = globals.userModel.create("test@test.test", "", false).get
      val bidder = globals.userModel.create("test2@test.test", "", false).get

      globals.userModel.addFakeMoney(asker, "LTC", 2)
      globals.userModel.addFakeMoney(bidder, "USD", 2)

      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 1, 1, false)
      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 1, 1, false)
      globals.engineModel.askBid(Some(bidder), None, "LTC", "USD", 1.5, 1, true)

      val asker_res = globals.engineModel.balance(Some(asker), None)
      val bidder_res = globals.engineModel.balance(Some(bidder), None)
      asker_res should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap
        .updated("USD", (BigDecimal("1.50000000") - BigDecimal("1.50000000") * fee, BigDecimal("0")))
        .updated("LTC", (BigDecimal("0.50000000"), BigDecimal("0.50000000")))
      bidder_res should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap
        .updated("USD", (BigDecimal("0.50000000"), BigDecimal("0")))
        .updated("LTC", (BigDecimal("1.50000000") - BigDecimal("1.50000000") * fee, BigDecimal("0")))
    }

    "be able to make a match and get charged the right fee" in new WithCleanTestDbApplication {
      val asker = globals.userModel.create("test@test.test", "", false).get
      val bidder = globals.userModel.create("test2@test.test", "", false).get

      globals.userModel.addFakeMoney(asker, "LTC", 1)
      globals.userModel.addFakeMoney(bidder, "USD", 2)

      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 1, 2, false)
      globals.engineModel.askBid(Some(bidder), None, "LTC", "USD", 1, 2, true)

      val asker_res = globals.engineModel.balance(Some(asker), None)
      val bidder_res = globals.engineModel.balance(Some(bidder), None)
      asker_res should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("USD", (2 - 2 * fee, 0))
      bidder_res should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("LTC", (1 - fee, 0))
    }

    "be able to make a match and get charged the right fee if we swap the bid and the ask" in new WithCleanTestDbApplication {
      val asker = globals.userModel.create("test@test.test", "", false).get
      val bidder = globals.userModel.create("test2@test.test", "", false).get

      globals.userModel.addFakeMoney(asker, "LTC", 1)
      globals.userModel.addFakeMoney(bidder, "USD", 2)

      globals.engineModel.askBid(Some(bidder), None, "LTC", "USD", 1, 2, true)
      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 1, 2, false)

      val asker_res = globals.engineModel.balance(Some(asker), None)
      val bidder_res = globals.engineModel.balance(Some(bidder), None)
      asker_res should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("USD", (2 - 2 * fee, 0))
      bidder_res should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("LTC", (1 - fee, 0))
    }

    "be able to make a match and verify it in trade history" in new WithCleanTestDbApplication {
      val asker = globals.userModel.create("test@test.test", "", false).get
      val bidder = globals.userModel.create("test2@test.test", "", false).get

      globals.userModel.addFakeMoney(asker, "LTC", 1)
      globals.userModel.addFakeMoney(bidder, "USD", 2)

      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 1, 2, false)
      globals.engineModel.askBid(Some(bidder), None, "LTC", "USD", 1, 2, true)

      val asker_res = globals.userModel.tradeHistory(Some(asker), None)
      val bidder_res = globals.userModel.tradeHistory(Some(bidder), None)
      asker_res should be equalTo List(TradeHistory("1.00000000", "0.01000000", asker_res.head.created, "2.00000000", "LTC", "USD", "ask"))
      bidder_res should be equalTo List(TradeHistory("1.00000000", "0.00500000", bidder_res.head.created, "2.00000000", "LTC", "USD", "bid"))
    }

    "be able to make a self-match and verify it in balance and trade history" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", false).get

      globals.userModel.addFakeMoney(uid, "LTC", 1)
      globals.userModel.addFakeMoney(uid, "USD", 2)

      globals.engineModel.askBid(Some(uid), None, "LTC", "USD", 1, 2, false)
      globals.engineModel.askBid(Some(uid), None, "LTC", "USD", 1, 2, true)

      val balance_res = globals.engineModel.balance(Some(uid), None)
      val trade_res = globals.userModel.tradeHistory(Some(uid), None)
      balance_res should be equalTo globals.metaModel.currencies.map {
        case "USD" => ("USD", (BigDecimal(2) - BigDecimal(2) * fee, BigDecimal(0)))
        case "LTC" => ("LTC", (BigDecimal(1) - BigDecimal(1) * fee, BigDecimal(0)))
        case c: String => (c, (BigDecimal(0), BigDecimal(0)))
      }.toMap
      trade_res.toSet should be equalTo Set(
        TradeHistory("1.00000000", "0.01000000", trade_res.head.created, "2.00000000", "LTC", "USD", "ask"),
        TradeHistory("1.00000000", "0.00500000", trade_res.head.created, "2.00000000", "LTC", "USD", "bid"))
    }

    "be able to cancel a transaction" in new WithCleanTestDbApplication {
      val asker = globals.userModel.create("test@test.test", "", false).get
      val bidder = globals.userModel.create("test2@test.test", "", false).get

      globals.userModel.addFakeMoney(asker, "LTC", 1)
      globals.userModel.addFakeMoney(bidder, "USD", 2)

      globals.engineModel.askBid(Some(bidder), None, "LTC", "USD", 1, 2, true)
      val id = globals.engineModel.userPendingTrades(Some(bidder), None).head.id
      val cancel_res = globals.engineModel.cancel(Some(bidder), None, id)
      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 1, 2, false)

      val asker_res = globals.engineModel.balance(Some(asker), None)
      val bidder_res = globals.engineModel.balance(Some(bidder), None)
      cancel_res should beTrue
      asker_res should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("LTC", (1, 1))
      bidder_res should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("USD", (2, 0))
    }

    "be able to make withdraw request with fees" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", false).get

      val feeConstant = 1
      val feeLinear = 0.01
      globals.engineModel.setFees("LTC", "blockchain", 0, 0, feeConstant, feeLinear)

      globals.userModel.addFakeMoney(uid, "LTC", 1000)

      globals.engineModel.withdraw(uid, "LTC", 12.3, "addr", None) mustNotEqual None
      val pending = globals.engineModel.pendingWithdrawals(uid)

      pending("LTC").head.amount must beEqualTo("12.30000000")
      pending("LTC").head.fee must beEqualTo(BigDecimal((1e8 * (12.3 * feeLinear + feeConstant)).toInt, 8).bigDecimal.toPlainString)
      pending("LTC").head.info must beEqualTo("addr")
    }

    "validate crypto addresses" in new WithCleanTestDbApplication {
      val validAddresses = List(
        ("12KYrjTdVGjFMtaxERSk3gphreJ5US8aUP", "BTC", false),
        ("12QeMLzSrB8XH8FvEzPMVoRxVAzTr5XM2y", "BTC", false),
        ("1oNLrsHnBcR6dpaBpwz3LSwutbUNkNSjs", "BTC", false),
        ("mzBc4XEFSdzCDcTxAgf6EZXgsZWpztRhef", "BTC", true),

        ("3NJZLcZEEYBpxYEUGewU4knsQRn1WM5Fkt", "BTC", false),
        ("2MxKEf2su6FGAUfCEAHreGFQvEYrfYNHvL7", "BTC", true),

        ("LVg2kJoFNg45Nbpy53h7Fe1wKyeXVRhMH9", "LTC", false),
        ("LTpYZG19YmfvY2bBDYtCKpunVRw7nVgRHW", "LTC", false),
        ("Lb6wDP2kHGyWC7vrZuZAgV7V4ECyDdH7a6", "LTC", false),
        ("mzBc4XEFSdzCDcTxAgf6EZXgsZWpztRhef", "LTC", true),

        ("3NJZLcZEEYBpxYEUGewU4knsQRn1WM5Fkt", "LTC", false),
        ("2MxKEf2su6FGAUfCEAHreGFQvEYrfYNHvL7", "LTC", true),

        ("PHCEsP6od3WJ8K2WKWEDBYKhH95pc9kiZN", "PPC", false),
        ("PSbM1pGoE9dnAuVWvpQqTTYVpKZU41dNAz", "PPC", false),
        ("PUULeHrJL2WujJkorc2RsUAR3SardKUauu", "PPC", false),
        ("mzBc4XEFSdzCDcTxAgf6EZXgsZWpztRhef", "PPC", true),

        ("pNms4CaWqgZUxbNZaA1yP2gPr3BYnez9EM", "PPC", false),
        ("2MxKEf2su6FGAUfCEAHreGFQvEYrfYNHvL7", "PPC", true),

        ("AVKeiZ5JadfWdH2EYVgVRfX4ufoyd4ehuM", "XPM", false),
        ("AQXBRPyob4dywUJ21RUKrR1xetQCDVenKD", "XPM", false),
        ("ANHfTZnskKqaBU7oZuSha9SpbHU3YBfeKf", "XPM", false),
        ("mzBc4XEFSdzCDcTxAgf6EZXgsZWpztRhef", "XPM", true),

        ("af5CvTQq7agDh717Wszb5QDbWb7nT2mukP", "XPM", false),
        ("2MxKEf2su6FGAUfCEAHreGFQvEYrfYNHvL7", "XPM", true)
      )

      for ((address, currency, testnet) <- validAddresses) {
        // Check if valid addresses are accepted
        CryptoAddress.isValid(address, currency, testnet) should be equalTo true
      }

      val addresses = ("", "", false) ::
        ("%%@", "", false) ::
        ("1A1zP1ePQGefi2DMPTifTL5SLmv7DivfNa", "", false) ::
        ("bd839e4f6fadb293ba580df5dea7814399989983", "", false) ::
        validAddresses

      val currencies = validAddresses.map(_._2).distinct

      currencies.foreach { c =>
        // Check if invalid addresses and addresses for other currencies are rejected
        for ((address, currency, testnet) <- addresses if currency != c) {
          // Exclude P2SH addresses starting with '3' as they are valid for multiple currencies
          if (!address.startsWith("3")) {
            CryptoAddress.isValid(address, c, false) should be equalTo false
          }
          // Exclude P2SH addresses starting with '2' and testnet addresses starting with 'm'
          if (!address.startsWith("2") && !address.startsWith("m")) {
            CryptoAddress.isValid(address, c, true) should be equalTo false
          }
        }
      }
    }
  }
}
