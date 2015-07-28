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

package wallet

import java.io.File
import play.api.i18n.{ DefaultLangs, DefaultMessagesApi, Lang, Messages }
import play.api.{ Mode, Play }
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

  private var lastBlockRead: Int = _
  private var lastWithdrawalTimeReceived: Int = _
  private var lastWithdrawalBlock: Int = _
  private var firstUpdate: Boolean = _
  private var changeAddressCount: Int = _
  private var balance: BigDecimal = _
  private var refillRequested: Boolean = _

  // Cache of pending deposits, initialized from DB
  private var pendingDeposits: mutable.Map[Deposit, Long] = _
  // Cache of confirmed deposits, populated on first run of update()
  private var confirmedDeposits: mutable.Set[Deposit] = _
  // Last batch withdrawal created, initialized from DB
  private var sentWithdrawalTx: Option[(Long, String)] = _
  // Last withdrawal if it could not be sent
  private var stalledWithdrawalTx: Option[(Long, BigDecimal, Map[String, BigDecimal])] = _

  private def initMemberVars(): Unit = {
    walletModel.getLastBlockRead(currency, nodeId) match {
      case (lastBlock, withdrawalTimeReceived) if lastBlock > minConfirmations =>
        lastBlockRead = lastBlock
        lastWithdrawalTimeReceived = withdrawalTimeReceived
      case (lastBlock, withdrawalTimeReceived) =>
        lastBlockRead = minConfirmations
        lastWithdrawalTimeReceived = withdrawalTimeReceived
    }
    lastWithdrawalBlock = lastBlockRead
    firstUpdate = true
    changeAddressCount = params.addressPool / 2
    balance = walletModel.getBalance(currency, nodeId)
    refillRequested = false
    pendingDeposits = mutable.Map(walletModel.getPendingDeposits(currency, nodeId): _*)
    confirmedDeposits = mutable.Set.empty
    sentWithdrawalTx = walletModel.getUnconfirmedWithdrawalTx(currency, nodeId)
    stalledWithdrawalTx = sentWithdrawalTx match {
      // If a withdrawal was created but not marked as sent
      case Some((id, "")) =>
        val withdrawalId = id
        // Set sentWithdrawalTx to the last confirmed withdrawal for verification
        sentWithdrawalTx = walletModel.getLastConfirmedWithdrawalTx(currency, nodeId)
        val withdrawals = walletModel.getWithdrawalTxData(withdrawalId)
        val withdrawalsTotal = withdrawals.view.map(_._2).sum
        Some(withdrawalId, withdrawalsTotal, walletModel.getColdStorageTransfer(withdrawalId) match {
          case Some((address, value)) =>
            withdrawals + (address -> value)
          case _ =>
            withdrawals
        })
      case _ =>
        None
    }
  }

  // Check for new deposits every average block time
  // Check available addresses every 2 hours
  val (blockTimer, addressTimer) = {
    initMemberVars()
    if (test) {
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
  }

  def update(): Unit = {
    Logger.debug("[wallet] [%s, %s] running update".format(currency, nodeId))
    val blockHeight = try {
      getBlockCount
    } catch {
      case _: Throwable =>
        Logger.error("[wallet] [%s, %s] cannot get block count from RPC".format(currency, nodeId))
        return
    }
    if (blockHeight <= lastBlockRead) {
      Logger.debug("[wallet] [%s, %s] no new blocks".format(currency, nodeId))
      return
    }

    try {
      Logger.info("[wallet] [%s, %s] Begin processing transactions up to block %s".format(currency, nodeId, blockHeight))
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
      val recoverWithdrawalTxData: Option[mutable.Map[String, BigDecimal]] = if (firstUpdate && stalledWithdrawalTx.isDefined) {
        Some(mutable.Map.empty)
      } else {
        None
      }

      while (transactionIterator.hasNext) {
        val transaction = transactionIterator.next()
        val category: String = transaction.get("category").textValue()
        val confirmations: Int = transaction.get("confirmations").asInt()
        val address: String = transaction.get("address").textValue()
        val amount: BigDecimal = transaction.get("amount").decimalValue()
        val txid: String = transaction.get("txid").textValue()

        Logger.debug("[wallet] [%s, %s] processing transaction %s of type %s for %s with %s confirmations for address %s".format(currency, nodeId, txid, category, amount, confirmations, address))

        if (category == "send") {
          if (sentWithdrawalTx.isDefined || recoverWithdrawalTxData.isDefined) {
            val timeReceived: Int = transaction.get("timereceived").asInt()
            if (confirmations < withdrawalConfirmations && confirmations >= 0 && timeReceived > withdrawalTimeReceived) {
              withdrawalConfirmations = confirmations
              withdrawalTxHash = txid
              // Withdrawal fees are negative so take the absolute value
              withdrawalTxFee = transaction.get("fee").decimalValue().abs()
              withdrawalTimeReceived = timeReceived
              if (recoverWithdrawalTxData.isDefined) {
                recoverWithdrawalTxData.get.clear()
              }
            }
            if (recoverWithdrawalTxData.isDefined && txid == withdrawalTxHash) {
              // Withdrawal amounts are negative so take the absolute value
              recoverWithdrawalTxData.get.put(address, amount.abs)
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

      // Recover if there is an unconfirmed withdrawal that may or may not have been sent
      if (recoverWithdrawalTxData.isDefined) {
        sentWithdrawalTx match {
          case Some((id, txHash)) if txHash == withdrawalTxHash && withdrawalTimeReceived >= lastWithdrawalTimeReceived =>
            // The most recent withdrawal was previously confirmed so the stalled withdrawal was never sent
            sentWithdrawalTx = None
          case _ =>
            stalledWithdrawalTx match {
              case Some((withdrawalId, withdrawalsTotal, withdrawals)) if withdrawalTxHash != "" && withdrawals == recoverWithdrawalTxData.get =>
                // The most recent withdrawal was the last unconfirmed withdrawal so update its status as sent
                walletModel.sentWithdrawalTx(withdrawalId, withdrawalTxHash, withdrawalsTotal)
                sentWithdrawalTx = Some(withdrawalId, withdrawalTxHash)
              case _ =>
                Logger.warn("[wallet] [%s, %s] Unexpected most recent withdrawal with tx hash %s".format(currency, nodeId, withdrawalTxHash))
                sentWithdrawalTx = None
            }
            // The most recent withdrawal was not the last to confirm so do not resend the last unconfirmed withdrawal
            stalledWithdrawalTx = None
        }
      }
      // If last batch withdrawal is confirmed, send the next batch
      if (withdrawalConfirmations >= minWithdrawalConfirmations) {
        lastWithdrawalBlock = withdrawalConfirmations
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
                  val withdrawals = walletModel.getWithdrawalTxData(withdrawalId)
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

      // Notify that the wallet needs refilling
      if (balance <= balanceWarn && params.refillEmail.isDefined) {
        if (!refillRequested) {
          refillRequested = true
          try {
            // XXX: temporary hack to make Messages work in emails (only english for now)
            implicit val messages = new Messages(new Lang("en", "US"), new DefaultMessagesApi(play.api.Environment.simple(new File("."), Mode.Prod),
              play.api.Play.current.configuration,
              new DefaultLangs(play.api.Play.current.configuration))
            )

            securesocial.core.providers.utils.Mailer.sendRefillWalletEmail(params.refillEmail.get, currency.toString, nodeId, balance, balanceTarget)
          } catch {
            case ex: Throwable =>
              // If email cannot be sent, log an error
              Logger.error("[wallet] [%s, %s] Error sending wallet refill email".format(currency, nodeId))
          }
        }
      } else {
        refillRequested = false
      }

      // Subtract minConfirmations as it could be decreased when actor is restarted
      // Make sure we have a withdrawal to check against for crash recovery
      walletModel.setLastBlockRead(currency, nodeId, math.min(blockHeight - minConfirmations, lastWithdrawalBlock), lastWithdrawalTimeReceived)
      lastBlockRead = blockHeight
      firstUpdate = false
      Logger.info("[wallet] [%s, %s] Finished processing transactions up to block %s".format(currency, nodeId, blockHeight))
    } catch {
      case ex: Throwable =>
        Logger.error("[wallet] [%s, %s] error processing transactions: %s".format(currency, nodeId, ex))
        // Reset state
        initMemberVars()
    }
  }

  def generateAddresses(): Unit = {
    val freeAddressCount = try {
      walletModel.getFreeAddressCount(currency, nodeId).toInt
    } catch {
      case _: Throwable =>
        Logger.error("[wallet] [%s, %s] cannot get free address count".format(currency, nodeId))
        return
    }
    if (freeAddressCount < params.addressPool / 2 || changeAddressCount > params.addressPool / 2) {
      val generateAddressCount = params.addressPool - freeAddressCount
      try {
        val addresses = List.range(0, generateAddressCount).map(i => getNewAddress)
        walletModel.addNewAddressBatch(currency, nodeId, addresses)
      } catch {
        case _: Throwable =>
          Logger.error("[wallet] [%s, %s] cannot get new addresses from RPC".format(currency, nodeId))
          return
      }
      // Refill the key pool before backing up the wallet
      try {
        keyPoolRefill()
      } catch {
        case _: Throwable =>
          Logger.error("[wallet] [%s, %s] cannot refill keypool from RPC".format(currency, nodeId))
          return
      }
      // back up the wallet only after we've generated new keys
      if (params.backupPath.isDefined) {
        try {
          backupWallet(params.backupPath.get)
          Logger.info("[wallet] [%s, %s] Backed up wallet to %s".format(currency, nodeId, params.backupPath.get))
        } catch {
          case _: Throwable =>
            Logger.error("[wallet] [%s, %s] cannot backup wallet from RPC".format(currency, nodeId))
            return
        }
      }
      changeAddressCount = 0
    }
  }

  private def backupWallet(destination: String) = {
    rpc.invoke("backupwallet", Array(destination), classOf[Any])
  }

  private def getBalance = {
    // This method is unused as balance is now tracked in the database
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
  def props(rpc: JsonRpcHttpClient, currency: CryptoCurrency, nodeId: Int, params: WalletParams, walletModel: WalletModel, test: Boolean = false) = {
    Props(classOf[Wallet], rpc, currency, nodeId, params, walletModel, test)
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
    coldAddress: Option[String],
    refillEmail: Option[String])
}

