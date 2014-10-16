// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package test

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test.{ FakeApplication, WithApplication }
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import play.api.test.Helpers.inMemoryDatabase
import org.specs2.mutable._

import play.api.test._

import org.specs2.mock._
import wallet.{ WalletModel, Wallet }
import com.googlecode.jsonrpc4j.JsonRpcHttpClient
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ObjectMapper
import wallet.Wallet.CryptoCurrency.CryptoCurrency
import akka.testkit.TestActorRef
import play.libs.Akka
import models.EngineModel
import wallet.WalletModel.Deposit
import scala.concurrent.duration._
import org.specs2.execute.AsResult
import org.mockito.Matchers._
import wallet.Wallet.{ WalletParams, CryptoCurrency }
import wallet.Wallet.CryptoCurrency.CryptoCurrency
import helpers._

@RunWith(classOf[JUnitRunner])
class WalletSpec extends Specification with Mockito {

  val walletParams = WalletParams(
    checkDelay = Duration(9999, SECONDS),
    checkInterval = Duration(9999, SECONDS),
    addressDelay = Duration(9999, SECONDS),
    addressInterval = Duration(9999, SECONDS),
    addressPool = 60,
    backupPath = None,
    coldAddress = None)

  "Wallet" should {
    // the application is used for its actor system and database connection
    "be able to receive deposits" in new WithCleanTestDbApplication {

      // we are testing an actor
      implicit val actorSystem = Akka.system()

      // assemble a mock wallet to deposit into and a mock rpc to make calls into
      val walletModel = mock[WalletModel]
      val rpc = mock[JsonRpcHttpClient]

      // first we fake the call to get the block number
      val blockCount = 10
      rpc.invoke("getblockcount", null, classOf[Int]) returns blockCount
      rpc.invoke("getbalance", null, classOf[BigDecimal]) returns BigDecimal(9999)

      // we mock the call to get the transactions
      val m = new ObjectMapper()
      val list = m.readTree(
        """
          {
            "transactions": [
              {
                "account": "",
                "address": "masdfasdfasdf",
                "category": "receive",
                "amount": 12.34,
                "confirmations": 4,
                "txid": "sha256txhash",
                "time": 1400000000,
                "timereceived": 1400000000
              }
            ]
          }
        """).asInstanceOf[ObjectNode]
      rpc.invoke(same("listsinceblock"), any[Array[String]], same(classOf[ObjectNode])) returns list

      // Warning: If we don't mock out these methods they'll return null
      walletModel.getUnconfirmedWithdrawalTx(any[CryptoCurrency], any[Int]) returns None
      walletModel.getPendingDeposits(any[CryptoCurrency], any[Int]) returns List()
      walletModel.getLastBlockRead(any[CryptoCurrency], any[Int]) returns ((blockCount - 1, 0))
      walletModel.obtainSessionLock(any[CryptoCurrency], any[Int]) returns true
      walletModel.getMinConfirmations(any[CryptoCurrency]) returns ((true, 3, 3))
      walletModel.getNodeInfo(any[CryptoCurrency], any[Int]) returns ((false, BigDecimal(0), BigDecimal(0), BigDecimal(10000), BigDecimal(84000000)))

      // and we mock the deposit into the database
      walletModel.createConfirmedDeposit(any[CryptoCurrency], any[Int], any[Deposit]) answers { _ => true }

      // this is our system under test with mocked out engine and rpc parameters
      val walletActor = TestActorRef(new Wallet(rpc, Wallet.CryptoCurrency.LTC, 0, walletParams, walletModel))
      val wallet = walletActor.underlyingActor

      wallet.update()

      // make sure the correct deposit was made
      there was one(walletModel).createConfirmedDeposit(Wallet.CryptoCurrency.LTC, 0, Deposit("masdfasdfasdf", BigDecimal(12.34), "sha256txhash"))

    }

    "be able to receive a deposit in the db" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", "", false).get
      val result1 = globals.engineModel.balance(uid)
      // start with empty account
      result1 should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap

      // we are testing an actor
      implicit val actorSystem = Akka.system()

      // assemble a mock rpc to make calls into
      val rpc = mock[JsonRpcHttpClient]

      // Associate the address manually
      globals.walletModel.addNewAddress(Wallet.CryptoCurrency.LTC, 0, "masdfasdfasdf")
      // Retrieves addresses and assigns a new one if needed
      globals.engineModel.addresses(uid, Wallet.CryptoCurrency.LTC.toString)

      // first we fake the call to get the block number
      val blockCount = 99999999
      rpc.invoke("getblockcount", null, classOf[Int]) returns blockCount
      rpc.invoke("getbalance", null, classOf[BigDecimal]) returns BigDecimal(9999)

      // we mock the call to get the transactions
      val m = new ObjectMapper()
      val list = m.readTree(
        """
          {
            "transactions": [
              {
                "account": "",
                "address": "masdfasdfasdf",
                "category": "receive",
                "amount": 12.34,
                "confirmations": 100,
                "txid": "sha256txhash",
                "time": 1400000000,
                "timereceived": 1400000000
              }
            ]
          }
        """).asInstanceOf[ObjectNode]
      rpc.invoke(same("listsinceblock"), any[Array[String]], same(classOf[ObjectNode])) returns list

      // this is our system under test with mocked out engine and rpc parameters
      val walletActor = TestActorRef(new Wallet(rpc, Wallet.CryptoCurrency.LTC, 0, walletParams, globals.walletModel))
      val wallet = walletActor.underlyingActor

      wallet.update()

      // make sure the correct deposit was made
      val result = globals.engineModel.balance(uid)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("LTC", (BigDecimal(12.34), BigDecimal(0)))
    }

    "be able to see pending deposit in the db" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", "", false).get
      val result1 = globals.engineModel.balance(uid)
      // start with empty account
      result1 should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap

      // we are testing an actor
      implicit val actorSystem = Akka.system()

      // assemble a mock rpc to make calls into
      val rpc = mock[JsonRpcHttpClient]

      // Associate the address manually
      globals.walletModel.addNewAddress(Wallet.CryptoCurrency.LTC, 0, "masdfasdfasdf")
      // Retrieves addresses and assigns a new one if needed
      globals.engineModel.addresses(uid, Wallet.CryptoCurrency.LTC.toString)

      // first we fake the call to get the block number
      val blockCount = 99999999
      rpc.invoke("getblockcount", null, classOf[Int]) returns blockCount
      rpc.invoke("getbalance", null, classOf[BigDecimal]) returns BigDecimal(9999)

      // we mock the call to get the transactions
      val m = new ObjectMapper()
      val list = m.readTree(
        """
          {
            "transactions": [
              {
                "account": "",
                "address": "masdfasdfasdf",
                "category": "receive",
                "amount": 12.34,
                "confirmations": 1,
                "txid": "sha256txhash",
                "time": 1400000000,
                "timereceived": 1400000000
              }
            ]
          }
        """).asInstanceOf[ObjectNode]
      rpc.invoke(same("listsinceblock"), any[Array[String]], same(classOf[ObjectNode])) returns list

      // this is our system under test with mocked out engine and rpc parameters
      val walletActor = TestActorRef(new Wallet(rpc, Wallet.CryptoCurrency.LTC, 0, walletParams, globals.walletModel))
      val wallet = walletActor.underlyingActor

      wallet.update()

      val result2 = globals.engineModel.pendingDeposits(uid)
      result2.size should be equalTo 1

      // make sure the correct deposit was made
      val result = globals.engineModel.balance(uid)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap
    }

    "be charged the right fees for deposits" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", "", false).get

      // we are testing an actor
      implicit val actorSystem = Akka.system()

      val feeAmt = 1
      val feePct = 0.01
      globals.engineModel.setFees("LTC", "blockchain", feeAmt, feePct, 0, 0)

      // assemble a mock rpc to make calls into
      val rpc = mock[JsonRpcHttpClient]

      // Associate the address manually
      globals.walletModel.addNewAddress(Wallet.CryptoCurrency.LTC, 0, "masdfasdfasdf")
      // Retrieves addresses and assigns a new one if needed
      globals.engineModel.addresses(uid, Wallet.CryptoCurrency.LTC.toString)

      // first we fake the call to get the block number
      val blockCount = 99999999
      rpc.invoke("getblockcount", null, classOf[Int]) returns blockCount
      rpc.invoke("getbalance", null, classOf[BigDecimal]) returns BigDecimal(9999)

      // we mock the call to get the transactions
      val m = new ObjectMapper()
      val list = m.readTree(
        """
          {
            "transactions": [
              {
                "account": "",
                "address": "masdfasdfasdf",
                "category": "receive",
                "amount": 12.34,
                "confirmations": 100,
                "txid": "sha256txhash",
                "time": 1400000000,
                "timereceived": 1400000000
              }
            ]
          }
        """).asInstanceOf[ObjectNode]
      rpc.invoke(same("listsinceblock"), any[Array[String]], same(classOf[ObjectNode])) returns list

      // this is our system under test with mocked out engine and rpc parameters
      val walletActor = TestActorRef(new Wallet(rpc, Wallet.CryptoCurrency.LTC, 0, walletParams, globals.walletModel))
      val wallet = walletActor.underlyingActor

      wallet.update()

      // make sure the correct deposit was made
      val result = globals.engineModel.balance(uid)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("LTC", (BigDecimal(12.34 - feeAmt - 12.34 * feePct), BigDecimal(0)))
    }

    "be charged the right fees for deposits not confirmed right away" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", "", false).get

      // we are testing an actor
      implicit val actorSystem = Akka.system()

      val feeAmt = 1
      val feePct = 0.01
      globals.engineModel.setFees("LTC", "blockchain", feeAmt, feePct, 0, 0)

      // assemble a mock rpc to make calls into
      val rpc = mock[JsonRpcHttpClient]

      // Associate the address manually
      globals.walletModel.addNewAddress(Wallet.CryptoCurrency.LTC, 0, "masdfasdfasdf")
      // Retrieves addresses and assigns a new one if needed
      globals.engineModel.addresses(uid, Wallet.CryptoCurrency.LTC.toString)

      // first we fake the call to get the block number
      var blockCount = 99999999
      rpc.invoke("getblockcount", null, classOf[Int]) answers (_ => blockCount)
      rpc.invoke("getbalance", null, classOf[BigDecimal]) returns BigDecimal(9999)

      // we mock the call to get the transactions
      val m = new ObjectMapper()
      var list = m.readTree(
        """
          {
            "transactions": [
              {
                "account": "",
                "address": "masdfasdfasdf",
                "category": "receive",
                "amount": 12.34,
                "confirmations": 1,
                "txid": "sha256txhash",
                "time": 1400000000,
                "timereceived": 1400000000
              }
            ]
          }
        """).asInstanceOf[ObjectNode]

      rpc.invoke(same("listsinceblock"), any[Array[String]], same(classOf[ObjectNode])) answers (_ => list)

      // this is our system under test with mocked out engine and rpc parameters
      val walletActor = TestActorRef(new Wallet(rpc, Wallet.CryptoCurrency.LTC, 0, walletParams, globals.walletModel))
      val wallet = walletActor.underlyingActor

      wallet.update()

      val result2 = globals.engineModel.pendingDeposits(uid)
      result2.size should be equalTo 1

      // Now confirm the deposit
      blockCount += 100
      list = m.readTree(
        """
          {
            "transactions": [
              {
                "account": "",
                "address": "masdfasdfasdf",
                "category": "receive",
                "amount": 12.34,
                "confirmations": 100,
                "txid": "sha256txhash",
                "time": 1400000000,
                "timereceived": 1400000000
              }
            ]
          }
        """).asInstanceOf[ObjectNode]
      wallet.update()

      val result3 = globals.engineModel.pendingDeposits(uid)
      result3.size should be equalTo 0

      // make sure the correct deposit was made
      val result = globals.engineModel.balance(uid)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap.updated("LTC", (BigDecimal(12.34 - feeAmt - 12.34 * feePct), BigDecimal(0)))
    }

    "send a withdrawal and get charged the right fee" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", "", false).get

      globals.userModel.addFakeMoney(uid, "LTC", 2)

      // we are testing an actor
      implicit val actorSystem = Akka.system()

      val feeAmt = 1
      val feePct = 0.01
      globals.engineModel.setFees("LTC", "blockchain", 0, 0, feeAmt, feePct)

      globals.engineModel.withdraw(uid, "LTC", 2, "masdfasdfasdf")

      // assemble a mock rpc to make calls into
      val rpc = mock[JsonRpcHttpClient]

      // first we fake the call to get the block number
      var blockCount = 99999999
      rpc.invoke("getblockcount", null, classOf[Int]) answers (_ => blockCount)
      rpc.invoke("getbalance", null, classOf[BigDecimal]) returns BigDecimal(9999)
      rpc.invoke(same("sendmany"), any[Array[Object]], same(classOf[String])) answers (_ => "sha256txhash")

      // we mock the call to get the transactions
      val m = new ObjectMapper()
      var list = m.readTree(
        """
          {
            "transactions": [
            ]
          }
        """).asInstanceOf[ObjectNode]

      rpc.invoke(same("listsinceblock"), any[Array[String]], same(classOf[ObjectNode])) answers (_ => list)

      // this is our system under test with mocked out engine and rpc parameters
      val walletActor = TestActorRef(new Wallet(rpc, Wallet.CryptoCurrency.LTC, 0, walletParams, globals.walletModel))
      val wallet = walletActor.underlyingActor

      wallet.update()

      val (resultId, resultHash) = globals.walletModel.getUnconfirmedWithdrawalTx(Wallet.CryptoCurrency.LTC, 0).getOrElse((0L, ""))
      resultHash shouldEqual "sha256txhash"

      // Now confirm the withdrawal
      blockCount += 100
      list = m.readTree(
        """
          {
            "transactions": [
              {
                "account": "",
                "address": "masdfasdfasdf",
                "category": "send",
                "amount": -0.98,
                "fee" : -0.00100000,
                "confirmations": 100,
                "txid": "sha256txhash",
                "time": 1400000000,
                "timereceived": 1400000000
              }
            ]
          }
        """).asInstanceOf[ObjectNode]
      wallet.update()

      val withdrawalTx = globals.walletModel.getWithdrawalTx(resultId)
      withdrawalTx should be equalTo Map("masdfasdfasdf" -> BigDecimal(0.98))

      val result = globals.engineModel.balance(uid)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap
    }

    "send a withdrawal that gets mutated" in new WithCleanTestDbApplication {
      val uid = globals.userModel.create("test@test.test", "", "", false).get

      globals.userModel.addFakeMoney(uid, "LTC", 2)

      // we are testing an actor
      implicit val actorSystem = Akka.system()

      val feeAmt = 1
      val feePct = 0.01
      globals.engineModel.setFees("LTC", "blockchain", 0, 0, feeAmt, feePct)

      globals.engineModel.withdraw(uid, "LTC", 2, "masdfasdfasdf")

      // assemble a mock rpc to make calls into
      val rpc = mock[JsonRpcHttpClient]

      // first we fake the call to get the block number
      var blockCount = 99999999
      rpc.invoke("getblockcount", null, classOf[Int]) answers (_ => blockCount)
      rpc.invoke("getbalance", null, classOf[BigDecimal]) returns BigDecimal(9999)
      rpc.invoke(same("sendmany"), any[Array[Object]], same(classOf[String])) answers (_ => "sha256txhash")

      // we mock the call to get the transactions
      val m = new ObjectMapper()
      var list = m.readTree(
        """
          {
            "transactions": [
            ]
          }
        """).asInstanceOf[ObjectNode]

      rpc.invoke(same("listsinceblock"), any[Array[String]], same(classOf[ObjectNode])) answers (_ => list)

      // this is our system under test with mocked out engine and rpc parameters
      val walletActor = TestActorRef(new Wallet(rpc, Wallet.CryptoCurrency.LTC, 0, walletParams, globals.walletModel))
      val wallet = walletActor.underlyingActor

      wallet.update()

      val (resultId, resultHash) = globals.walletModel.getUnconfirmedWithdrawalTx(Wallet.CryptoCurrency.LTC, 0).getOrElse((0L, ""))
      resultHash shouldEqual "sha256txhash"

      // Now confirm the withdrawal
      blockCount += 100
      list = m.readTree(
        """
          {
            "transactions": [
              {
                "account": "",
                "address": "masdfasdfasdf",
                "category": "send",
                "amount": -0.98,
                "fee" : -0.00100000,
                "confirmations": 100,
                "txid": "mutatedtxhash",
                "time": 1400000000,
                "timereceived": 1500000000
              },
              {
                "account": "",
                "address": "masdfasdfasdf",
                "category": "send",
                "amount": -0.98,
                "fee" : -0.00100000,
                "confirmations": 0,
                "txid": "sha256txhash",
                "time": 1400000000,
                "timereceived": 1400000000
              }
            ]
          }
        """).asInstanceOf[ObjectNode]
      wallet.update()

      val withdrawalTx = globals.walletModel.getWithdrawalTx(resultId)
      withdrawalTx should be equalTo Map("masdfasdfasdf" -> BigDecimal(0.98))

      val result = globals.engineModel.balance(uid)
      result should be equalTo globals.metaModel.currencies.map(_ -> (BigDecimal(0), BigDecimal(0))).toMap

      import globals._
      import play.api.db.DB
      import anorm._

      val (txFee, originalTxHash, mutatedTxHash) = DB.withConnection(walletModel.db) { implicit c =>
        SQL(
          """
            |select tx_fee, tx_hash, tx_hash_mutated
            |from withdrawals_crypto_tx wct inner join withdrawals_crypto_tx_mutated wctm
            |on wctm.id = wct.id where wct.id = {id} and
            |sent is not NULL and confirmed is not NULL
          """.stripMargin).on('id -> resultId)().map(row =>
            (row[BigDecimal]("tx_fee"), row[String]("tx_hash"), row[String]("tx_hash_mutated"))).head
      }

      txFee shouldEqual BigDecimal(0.001)
      originalTxHash shouldEqual "sha256txhash"
      mutatedTxHash shouldEqual "mutatedtxhash"
    }
  }
}
