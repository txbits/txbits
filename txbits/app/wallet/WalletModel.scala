// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package wallet

import anorm._
import play.api.db.DB
import play.api.Play.current
import WalletModel._
import wallet.Wallet.CryptoCurrency.CryptoCurrency

class WalletModel(val db: String = "default") {

  import globals.bigDecimalColumn

  def obtainSessionLock(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.obtainSessionLock.on('currency -> currency.id, 'node_id -> nodeId)().map(row => row[Boolean]("pg_try_advisory_lock")).head
  }

  def addNewAddress(currency: CryptoCurrency, nodeId: Int, address: String) = DB.withConnection(db) { implicit c =>
    SQLText.addNewAddress.on('currency -> currency.toString, 'node_id -> nodeId, 'address -> address).execute()
  }

  def addNewAddressBatch(currency: CryptoCurrency, nodeId: Int, addresses: List[String]) = DB.withConnection(db) { implicit c =>
    (SQLText.addNewAddress.asBatch /: addresses)(
      (sql, address) => sql.addBatchParams(address, currency.toString, nodeId)
    ).execute()
  }

  def getFreeAddressCount(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.getFreeAddressCount.on('currency -> currency.toString, 'node_id -> nodeId)().map(row => row[Long]("count")).head
  }

  def getMinConfirmations(currency: CryptoCurrency) = DB.withConnection(db) { implicit c =>
    SQLText.getMinConfirmations.on('currency -> currency.toString)().map(row => (
      row[Boolean]("active"),
      row[Int]("min_deposit_confirmations"),
      row[Int]("min_withdrawal_confirmations")
    )).head
  }

  def getNodeInfo(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.getNodeInfo.on('currency -> currency.toString, 'node_id -> nodeId)().map(row => (
      row[Boolean]("retired"),
      row[BigDecimal]("balance_min"),
      row[BigDecimal]("balance_warn"),
      row[BigDecimal]("balance_target"),
      row[BigDecimal]("balance_max")
    )).head
  }

  def getLastBlockRead(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.getLastBlockRead.on('currency -> currency.toString, 'node_id -> nodeId)().map(row => (
      row[Option[Int]]("last_block_read").getOrElse(0),
      row[Option[Int]]("last_withdrawal_time_received").getOrElse(0)
    )).head
  }

  def setLastBlockRead(currency: CryptoCurrency, nodeId: Int, blockCount: Int, lastWithdrawalTimeReceived: Int, balance: BigDecimal) = DB.withConnection(db) { implicit c =>
    SQLText.setLastBlockRead.on(
      'currency -> currency.toString,
      'node_id -> nodeId,
      'block_count -> blockCount,
      'last_withdrawal_time_received -> lastWithdrawalTimeReceived,
      'balance -> balance.bigDecimal
    ).execute()
  }

  def createDeposit(currency: CryptoCurrency, nodeId: Int, deposit: Deposit) = DB.withConnection(db) { implicit c =>
    SQLText.createDeposit.on(
      'currency -> currency.toString,
      'node_id -> nodeId,
      'address -> deposit.address,
      'amount -> deposit.amount.bigDecimal,
      'tx_hash -> deposit.txHash,
      'fee -> new java.math.BigDecimal(0)
    )().map(row => row[Long]("id")).head
  }

  def createConfirmedDeposit(currency: CryptoCurrency, nodeId: Int, deposit: Deposit) = DB.withConnection(db) { implicit c =>
    SQLText.createConfirmedDeposit.on(
      'currency -> currency.toString,
      'node_id -> nodeId,
      'address -> deposit.address,
      'amount -> deposit.amount.bigDecimal,
      'tx_hash -> deposit.txHash,
      'fee -> new java.math.BigDecimal(0)
    ).execute()
  }

  def isConfirmedDeposit(deposit: Deposit) = DB.withConnection(db) { implicit c =>
    SQLText.isConfirmedDeposit.on(
      'address -> deposit.address,
      'amount -> deposit.amount,
      'tx_hash -> deposit.txHash
    )().map(row => row[Boolean]("exists")).head
  }

  def getPendingDeposits(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.getPendingDeposits.on('currency -> currency.toString, 'node_id -> nodeId)().map(row =>
      Deposit(row[String]("address"), row[BigDecimal]("amount"), row[String]("tx_hash")) -> row[Long]("id")
    ).toList
  }

  def confirmedDeposit(deposit: Deposit, id: Long) = DB.withConnection(db) { implicit c =>
    SQLText.confirmedDeposit.on('id -> id, 'address -> deposit.address, 'tx_hash -> deposit.txHash).execute()
  }

  def getUnconfirmedWithdrawalTx(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.getUnconfirmedWithdrawalTx.on('currency -> currency.toString, 'node_id -> nodeId)().map(row => row[Long]("id") -> row[String]("tx_hash")).headOption
  }

  def createWithdrawalTx(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.createWithdrawalTx.on('currency -> currency.toString, 'node_id -> nodeId)().map(row => row[Long]("withdrawals_crypto_tx_id")).headOption
  }

  def getWithdrawalTx(txId: Long) = DB.withConnection(db) { implicit c =>
    SQLText.getWithdrawalTx.on('tx_id -> txId)().map(row => row[String]("address") -> row[BigDecimal]("value")).toMap
  }

  def sentWithdrawalTx(txId: Long, txHash: String) = DB.withConnection(db) { implicit c =>
    SQLText.sentWithdrawalTx.on('tx_id -> txId, 'tx_hash -> txHash).execute()
  }

  def confirmedWithdrawalTx(txId: Long, txFee: BigDecimal) = DB.withConnection(db) { implicit c =>
    SQLText.confirmedWithdrawalTx.on('tx_id -> txId, 'tx_fee -> txFee.bigDecimal).execute()
  }

  def createColdStorageTransfer(txId: Long, address: String, value: BigDecimal) = DB.withConnection(db) { implicit c =>
    SQLText.createColdStorageTransfer.on('tx_id -> txId, 'address -> address, 'value -> value.bigDecimal).execute()
  }

  def setWithdrawalTxHashMutated(txId: Long, txHash: String) = DB.withConnection(db) { implicit c =>
    SQLText.setWithdrawalTxHashMutated.on('tx_id -> txId, 'tx_hash -> txHash).execute()
  }

}

object WalletModel {
  case class Deposit(address: String, amount: BigDecimal, txHash: String)
}
