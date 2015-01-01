// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package wallet

import com.fasterxml.jackson.databind.node.ObjectNode
import com.googlecode.jsonrpc4j.JsonRpcHttpClient
import play.Logger
import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.collection.mutable
import scala.collection.JavaConverters._
import wallet.WalletModel._
import wallet.Wallet._
import wallet.Wallet.CryptoCurrency.CryptoCurrency

class Wallet(rpc: JsonRpcHttpClient, currency: CryptoCurrency, nodeId: Int, params: WalletParams, walletModel: WalletModel, test: Boolean = false) extends Actor {
  import context.system
  implicit val ec: ExecutionContext = system.dispatcher

  final val (active, minDepositConfirmations, minWithdrawalConfirmations) = walletModel.getMinConfirmations(currency)
  final val minConfirmations = math.max(minDepositConfirmations, minWithdrawalConfirmations)
  final val (retired, balanceMin, balanceWarn, balanceTarget, balanceMax) = walletModel.getNodeInfo(currency, nodeId)

  private var (lastBlockRead, lastWithdrawalTimeReceived) = walletModel.getLastBlockRead(currency, nodeId) match {
    case (lastBlock, withdrawalTimeReceived) if lastBlock > minConfirmations => (lastBlock, withdrawalTimeReceived)
    case (lastBlock, withdrawalTimeReceived) => (minConfirmations, withdrawalTimeReceived)
  }
  private var firstUpdate = true
  private var changeAddressCount = 0
  private var balance = walletModel.getBalance(currency, nodeId)

  // Cache of pending deposits, initialized from DB
  private val pendingDeposits: mutable.Map[Deposit, Long] = mutable.Map(walletModel.getPendingDeposits(currency, nodeId): _*)
  // Cache of confirmed deposits, initialized on first run of update()
  private var confirmedDeposits: mutable.Set[Deposit] = mutable.Set.empty
  // Last batch withdrawal created, initialized from DB
  private var sentWithdrawalTx: Option[(Long, String)] = walletModel.getUnconfirmedWithdrawalTx(currency, nodeId)
  // Last withdrawal if it could not be sent because the wallet had insufficient funds
  private var stalledWithdrawalTx: Option[(Long, BigDecimal, Map[String, BigDecimal])] = None

  // Check for new deposits every average block time
  // Check available addresses every 2 hours
  val (blockTimer, addressTimer) = if (test) {
    (null, null)
  } else if (active && !retired && walletModel.obtainSessionLock(currency, nodeId)) {
    (
      system.scheduler.schedule(params.checkDelay, params.checkInterval)(update()),
      system.scheduler.schedule(params.addressDelay, params.addressInterval)(generateAddresses())
    )
  } else {
    throw new RuntimeException("Wallet disabled or already running")
    (null, null)
  }

  def update(): Unit = {
    Logger.debug("[wallet] [%s, %s] running update".format(currency, nodeId))
    val blockHeight = getBlockCount
    if (blockHeight <= lastBlockRead) {
      Logger.debug("[wallet] [%s, %s] no new blocks for ".format(currency, nodeId))
      return
    }

    // Retrieve transactions up to the minimum number of confirmations required
    val lastBlockHash = getBlockHash(lastBlockRead - minConfirmations)
    val list = listSinceBlock(lastBlockHash)
    val transactions = list.get("transactions")
    val transactionIterator = transactions.elements()
    val sinceBlockConfirmedDeposits: mutable.Set[Deposit] = mutable.Set.empty
    var withdrawalConfirmations: Int = Int.MaxValue
    var withdrawalTxHash: String = ""
    var withdrawalTxFee: BigDecimal = 0
    var withdrawalTimeReceived: Int = lastWithdrawalTimeReceived

    while (transactionIterator.hasNext) {
      val transaction = transactionIterator.next()
      val category: String = transaction.get("category").textValue()
      val confirmations: Int = transaction.get("confirmations").asInt()
      val address: String = transaction.get("address").textValue()
      val amount: BigDecimal = transaction.get("amount").decimalValue()
      val txid: String = transaction.get("txid").textValue()

      Logger.debug("[wallet] [%s, %s] processing transaction %s of type %s for %s with %s confirmations for address %s".format(currency, nodeId, txid, category, amount, confirmations, address))

      if (category == "send") {
        if (sentWithdrawalTx.isDefined) {
          val timeReceived: Int = transaction.get("timereceived").asInt()
          if (confirmations < withdrawalConfirmations && confirmations >= 0 && timeReceived > withdrawalTimeReceived) {
            withdrawalConfirmations = confirmations
            withdrawalTxHash = txid
            // Withdrawal fees are negative so take the absolute value
            withdrawalTxFee = transaction.get("fee").decimalValue().abs()
            withdrawalTimeReceived = timeReceived
          }
        }
      } else if (category == "receive" && confirmations > 0) {
        val deposit = Deposit(address, amount, txid)

        if (confirmations < minDepositConfirmations) {
          // Unconfirmed deposit with at least 1 confirmation
          if (!pendingDeposits.contains(deposit)) {
            val id = walletModel.createDeposit(currency, nodeId, deposit)
            pendingDeposits.put(deposit, id)
          }
        } else {
          // Confirmed deposit
          if (confirmedDeposits.add(deposit)) {
            pendingDeposits.remove(deposit) match {
              case Some(id) =>
                walletModel.confirmedDeposit(deposit, id, nodeId)
              case _ =>
                // If confirmed deposits cache has not been initialized, check if the deposit is in the DB
                if (!firstUpdate || (firstUpdate && !walletModel.isConfirmedDeposit(deposit))) {
                  walletModel.createConfirmedDeposit(currency, nodeId, deposit)
                }
            }
          }
          sinceBlockConfirmedDeposits.add(deposit)
        }
      }
    }
    // Remove confirmed deposits that are no longer returned
    confirmedDeposits = confirmedDeposits & sinceBlockConfirmedDeposits

    // If last batch withdrawal is confirmed, send the next batch
    if (withdrawalConfirmations >= minWithdrawalConfirmations) {
      sentWithdrawalTx match {
        case Some((id, txHash)) if withdrawalTxHash != "" =>
          if (txHash != withdrawalTxHash)
            walletModel.setWithdrawalTxHashMutated(id, withdrawalTxHash)
          walletModel.confirmedWithdrawalTx(id, withdrawalTxFee)
          lastWithdrawalTimeReceived = withdrawalTimeReceived
        case _ =>
      }
      balance = walletModel.getBalance(currency, nodeId)
      sentWithdrawalTx = if (balance > balanceMin) {
        stalledWithdrawalTx match {
          case Some((withdrawalId, withdrawalsTotal, withdrawals)) if balance >= withdrawalsTotal =>
            val withdrawalTxHash = sendMany(withdrawals.asJava)
            walletModel.sentWithdrawalTx(withdrawalId, withdrawalTxHash, withdrawalsTotal)
            changeAddressCount += 1
            stalledWithdrawalTx = None
            Some(withdrawalId -> withdrawalTxHash)
          case Some((withdrawalId, withdrawalsTotal, withdrawals)) =>
            None
          case _ =>
            walletModel.createWithdrawalTx(currency, nodeId) match {
              case Some(withdrawalId) =>
                val withdrawals = walletModel.getWithdrawalTx(withdrawalId)
                val withdrawalsTotal = withdrawals.view.map(_._2).sum
                val balanceLowerBound = balance - withdrawalsTotal
                if (balanceLowerBound < 0) {
                  stalledWithdrawalTx = Some(withdrawalId, withdrawalsTotal, withdrawals)
                  None
                } else {
                  val withdrawalsJava = (
                    if (balanceLowerBound > balanceMax && params.coldAddress.isDefined) {
                      // Transfer to cold storage
                      val coldStorageValue = balanceLowerBound - balanceTarget
                      walletModel.createColdStorageTransfer(withdrawalId, params.coldAddress.get, coldStorageValue)
                      withdrawals + (params.coldAddress.get -> coldStorageValue)
                    } else {
                      withdrawals
                    }).asJava
                  val withdrawalTxHash = sendMany(withdrawalsJava)
                  walletModel.sentWithdrawalTx(withdrawalId, withdrawalTxHash, withdrawalsTotal)
                  changeAddressCount += 1
                  Some(withdrawalId -> withdrawalTxHash)
                }
              case _ =>
                None
            }
        }
      } else {
        None
      }
    }

    balance = walletModel.getBalance(currency, nodeId)

    if (balance <= balanceWarn) {
      //TODO: Notify that the wallet needs refilling
    }

    // Subtract minConfirmations as it could be decreased when actor is restarted
    walletModel.setLastBlockRead(currency, nodeId, blockHeight - minConfirmations, lastWithdrawalTimeReceived)
    lastBlockRead = blockHeight
    firstUpdate = false
  }

  def generateAddresses() = {
    val freeAddressCount = walletModel.getFreeAddressCount(currency, nodeId).toInt
    if (freeAddressCount < params.addressPool / 2 || changeAddressCount > params.addressPool / 2) {
      changeAddressCount = 0
      val generateAddressCount = params.addressPool - freeAddressCount
      val addresses = List.range(0, generateAddressCount).map(i => getNewAddress)
      walletModel.addNewAddressBatch(currency, nodeId, addresses)
      // Refill the key pool before backing up the wallet
      keyPoolRefill()
      // back up the wallet only after we've generated new keys
      if (params.backupPath.isDefined) {
        backupWallet(params.backupPath.get)
        Logger.info("Backed up wallet to %s".format(params.backupPath.get))
      }
    }
  }

  private def backupWallet(destination: String) = {
    rpc.invoke("backupwallet", Array(destination), classOf[Any])
  }

  private def getBalance = {
    rpc.invoke("getbalance", null, classOf[BigDecimal])
  }

  private def getBlockCount = {
    rpc.invoke("getblockcount", null, classOf[Int])
  }

  private def getBlockHash(index: Int) = {
    rpc.invoke("getblockhash", Array[Any](index), classOf[String])
  }

  private def getNewAddress = {
    rpc.invoke("getnewaddress", Array(), classOf[String])
  }

  private def keyPoolRefill() = {
    rpc.invoke("keypoolrefill", null, classOf[Any])
  }

  private def listSinceBlock(blockHash: String) = {
    rpc.invoke("listsinceblock", Array(blockHash), classOf[ObjectNode])
  }

  private def sendMany(withdrawals: java.util.Map[String, BigDecimal]) = {
    rpc.invoke("sendmany", Array("", withdrawals), classOf[String])
  }

  def receive = {
    case _ =>
  }

}

object Wallet {
  def props(rpc: JsonRpcHttpClient, currency: CryptoCurrency, nodeId: Int, params: WalletParams, walletModel: WalletModel) = {
    Props(classOf[Wallet], rpc, currency, nodeId, params, walletModel)
  }
  object CryptoCurrency extends Enumeration {
    type CryptoCurrency = Value
    val BTC = Value(0)
    val LTC = Value(1)
    val PPC = Value(2)
    val XPM = Value(3)
  }
  case class WalletParams(
    checkDelay: FiniteDuration,
    checkInterval: FiniteDuration,
    addressDelay: FiniteDuration,
    addressInterval: FiniteDuration,
    addressPool: Int,
    backupPath: Option[String],
    coldAddress: Option[String])
}

