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

import anorm._
import play.api.db.DB
import play.api.Play.current
import WalletModel._
import wallet.Wallet.CryptoCurrency.CryptoCurrency

class WalletModel(val db: String = "default") {

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
    SQLText.getFreeAddressCount.on('currency -> currency.toString, 'node_id -> nodeId)().map(row => row[Long]("free_address_count")).head
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

  def getBalance(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.getBalance.on('currency -> currency.toString, 'node_id -> nodeId)().map(row => row[BigDecimal]("get_balance")).head
  }

  def getLastBlockRead(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.getLastBlockRead.on('currency -> currency.toString, 'node_id -> nodeId)().map(row => (
      row[Option[Int]]("last_block_read").getOrElse(0),
      row[Option[Int]]("last_withdrawal_time_received").getOrElse(0)
    )).head
  }

  def setLastBlockRead(currency: CryptoCurrency, nodeId: Int, blockCount: Int, lastWithdrawalTimeReceived: Int) = DB.withConnection(db) { implicit c =>
    SQLText.setLastBlockRead.on(
      'currency -> currency.toString,
      'node_id -> nodeId,
      'block_count -> blockCount,
      'last_withdrawal_time_received -> lastWithdrawalTimeReceived
    ).execute()
  }

  def createDeposit(currency: CryptoCurrency, nodeId: Int, deposit: Deposit) = DB.withConnection(db) { implicit c =>
    SQLText.createDeposit.on(
      'currency -> currency.toString,
      'node_id -> nodeId,
      'address -> deposit.address,
      'amount -> deposit.amount.bigDecimal,
      'tx_hash -> deposit.txHash
    )().map(row => row[Long]("create_deposit")).head
  }

  def createConfirmedDeposit(currency: CryptoCurrency, nodeId: Int, deposit: Deposit) = DB.withConnection(db) { implicit c =>
    SQLText.createConfirmedDeposit.on(
      'currency -> currency.toString,
      'node_id -> nodeId,
      'address -> deposit.address,
      'amount -> deposit.amount.bigDecimal,
      'tx_hash -> deposit.txHash
    ).execute()
  }

  def isConfirmedDeposit(deposit: Deposit) = DB.withConnection(db) { implicit c =>
    SQLText.isConfirmedDeposit.on(
      'address -> deposit.address,
      'amount -> deposit.amount,
      'tx_hash -> deposit.txHash
    )().map(row => row[Boolean]("is_confirmed_deposit")).head
  }

  def getPendingDeposits(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.getPendingDeposits.on('currency -> currency.toString, 'node_id -> nodeId)().map(row =>
      Deposit(row[String]("address"), row[BigDecimal]("amount"), row[String]("tx_hash")) -> row[Long]("id")
    ).toList
  }

  def confirmedDeposit(deposit: Deposit, id: Long, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.confirmedDeposit.on('id -> id, 'address -> deposit.address, 'tx_hash -> deposit.txHash, 'node_id -> nodeId).execute()
  }

  def getUnconfirmedWithdrawalTx(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.getUnconfirmedWithdrawalTx.on('currency -> currency.toString, 'node_id -> nodeId)().map(row => row[Option[Long]]("id") -> row[Option[String]]("tx_hash")).head match {
      case (Some(id: Long), Some(txHash: String)) => Some(id, txHash)
      case _ => None
    }
  }

  def createWithdrawalTx(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQLText.createWithdrawalTx.on('currency -> currency.toString, 'node_id -> nodeId)().map(row => row[Option[Long]]("create_withdrawal_tx")).head
  }

  def getWithdrawalTx(txId: Long) = DB.withConnection(db) { implicit c =>
    SQLText.getWithdrawalTx.on('tx_id -> txId)().map(row => row[String]("address") -> row[BigDecimal]("value")).toMap
  }

  def sentWithdrawalTx(txId: Long, txHash: String, txAmount: BigDecimal) = DB.withConnection(db) { implicit c =>
    SQLText.sentWithdrawalTx.on('tx_id -> txId, 'tx_hash -> txHash, 'tx_amount -> txAmount).execute()
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
