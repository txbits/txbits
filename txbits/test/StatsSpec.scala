// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

import helpers.{ WithCleanTestDbApplicationReal, WithCleanTestDbApplication }
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.mutable._

import org.specs2.mock._
import models.TradeHistory
import controllers.IAPI.CryptoAddress

@RunWith(classOf[JUnitRunner])
class StatsSpec extends Specification with Mockito {

  val fee = globals.metaModel.tradeFees

  "Stats API" should {

    "update stats for the right pair after a bid matches" in new WithCleanTestDbApplication {
      val asker = globals.userModel.create("test@test.test", "", false).get
      val bidder = globals.userModel.create("test2@test.test", "", false).get

      globals.userModel.addFakeMoney(asker, "LTC", 1)
      globals.userModel.addFakeMoney(bidder, "USD", 1)

      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 1, 1, false)
      globals.engineModel.askBid(Some(bidder), None, "LTC", "USD", 1, 1, true)

      val stats_res = controllers.StatsAPI.APIv1.chartFromDB("LTC", "USD")
      stats_res.head.drop(1).map(_.value) should be equalTo Seq(1, 1, 1, 1, 1)
      val stats_res2 = controllers.StatsAPI.APIv1.chartFromDB("BTC", "USD")
      stats_res2 should beEmpty
    }

    "update stats after an ask matches" in new WithCleanTestDbApplication {
      val asker = globals.userModel.create("test@test.test", "", false).get
      val bidder = globals.userModel.create("test2@test.test", "", false).get

      globals.userModel.addFakeMoney(asker, "LTC", 1)
      globals.userModel.addFakeMoney(bidder, "USD", 1)

      globals.engineModel.askBid(Some(bidder), None, "LTC", "USD", 1, 1, true)
      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 1, 1, false)

      val stats_res = controllers.StatsAPI.APIv1.chartFromDB("LTC", "USD")
      stats_res.head.drop(1).map(_.value) should be equalTo Seq(1, 1, 1, 1, 1)
    }

    "not update stats for no matches" in new WithCleanTestDbApplication {
      val asker = globals.userModel.create("test@test.test", "", false).get
      val bidder = globals.userModel.create("test2@test.test", "", false).get

      globals.userModel.addFakeMoney(asker, "LTC", 1)
      globals.userModel.addFakeMoney(bidder, "USD", 1)

      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 1, 2, false)
      globals.engineModel.askBid(Some(bidder), None, "LTC", "USD", 1, 1, true)

      val stats_res = controllers.StatsAPI.APIv1.chartFromDB("LTC", "USD")
      stats_res should beEmpty
    }

    "update stats correctly after 3 matches" in new WithCleanTestDbApplication {
      val asker = globals.userModel.create("test@test.test", "", false).get
      val bidder = globals.userModel.create("test2@test.test", "", false).get

      globals.userModel.addFakeMoney(asker, "LTC", 10)
      globals.userModel.addFakeMoney(bidder, "USD", 10)

      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 1, 1, false)
      globals.engineModel.askBid(Some(bidder), None, "LTC", "USD", 2, 2, true)
      globals.engineModel.askBid(Some(asker), None, "LTC", "USD", 2, 1.5, false)
      globals.engineModel.askBid(Some(bidder), None, "LTC", "USD", 1, 1.5, true)

      val stats_res = controllers.StatsAPI.APIv1.chartFromDB("LTC", "USD")
      stats_res.head(1).value should be equalTo 1
      stats_res.head(2).value should be equalTo 2
      stats_res.head(3).value should be equalTo 1
      stats_res.head(4).value should be equalTo 1.5
      stats_res.head(5).value should be equalTo 3
    }
  }
}
