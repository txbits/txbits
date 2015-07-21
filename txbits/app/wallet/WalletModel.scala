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
    SQL""" select pg_try_advisory_lock(${currency.id}, $nodeId) """().map(row => row[Boolean]("pg_try_advisory_lock")).head
  }

  def addNewAddress(currency: CryptoCurrency, nodeId: Int, address: String) = DB.withConnection(db) { implicit c =>
    SQL"""
    insert into users_addresses (address, currency, node_id)
    values($address, ${currency.toString}, $nodeId)
    """.execute()
  }

  def addNewAddressBatch(currency: CryptoCurrency, nodeId: Int, addresses: List[String]) = DB.withConnection(db) { implicit c =>
    (SQL("""
      insert into users_addresses (address, currency, node_id)
      values({address}, {currency}, {node_id})"""
    ).asBatch /: addresses)(
      (sql, address) => sql.addBatchParams(address, currency.toString, nodeId)
    ).execute()
  }

  def getFreeAddressCount(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQL""" select free_address_count(${currency.toString}, $nodeId) """().map(row => row[Long]("free_address_count")).head
  }

  def getMinConfirmations(currency: CryptoCurrency) = DB.withConnection(db) { implicit c =>
    SQL""" select * from get_min_confirmations(${currency.toString}) """().map(row => (
      row[Boolean]("active"),
      row[Int]("min_deposit_confirmations"),
      row[Int]("min_withdrawal_confirmations")
    )).head
  }

  def getNodeInfo(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQL""" select * from get_node_info(${currency.toString}, $nodeId) """().map(row => (
      row[Boolean]("retired"),
      row[BigDecimal]("balance_min"),
      row[BigDecimal]("balance_warn"),
      row[BigDecimal]("balance_target"),
      row[BigDecimal]("balance_max")
    )).head
  }

  def getBalance(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQL""" select get_balance(${currency.toString}, $nodeId) """().map(row => row[BigDecimal]("get_balance")).head
  }

  def getLastBlockRead(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQL""" select * from get_last_block_read(${currency.toString}, $nodeId) """().map(row => (
      row[Option[Int]]("last_block_read").getOrElse(0),
      row[Option[Int]]("last_withdrawal_time_received").getOrElse(0)
    )).head
  }

  def setLastBlockRead(currency: CryptoCurrency, nodeId: Int, blockCount: Int, lastWithdrawalTimeReceived: Int) = DB.withConnection(db) { implicit c =>
    SQL"""
      select set_last_block_read(${currency.toString}, $nodeId,
      $blockCount, $lastWithdrawalTimeReceived)
    """.execute()
  }

  def createDeposit(currency: CryptoCurrency, nodeId: Int, deposit: Deposit) = DB.withConnection(db) { implicit c =>
    SQL""" select create_deposit(
      ${currency.toString},
      $nodeId,
      ${deposit.address},
      ${deposit.amount.bigDecimal},
      ${deposit.txHash})
      """().map(row => row[Long]("create_deposit")).head
  }

  def createConfirmedDeposit(currency: CryptoCurrency, nodeId: Int, deposit: Deposit) = DB.withConnection(db) { implicit c =>
    SQL"""
      select create_confirmed_deposit(
      ${currency.toString},
      $nodeId,
      ${deposit.address},
      ${deposit.amount.bigDecimal},
      ${deposit.txHash})
    """.execute()
  }

  def isConfirmedDeposit(deposit: Deposit) = DB.withConnection(db) { implicit c =>
    SQL""" select is_confirmed_deposit(
      ${deposit.address},
      ${deposit.amount.bigDecimal},
      ${deposit.txHash})
      """().map(row => row[Boolean]("is_confirmed_deposit")).head
  }

  def getPendingDeposits(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQL""" select * from get_pending_deposits(${currency.toString}, $nodeId) """().map(row =>
      Deposit(row[String]("address"), row[BigDecimal]("amount"), row[String]("tx_hash")) -> row[Long]("id")
    ).toList
  }

  def confirmedDeposit(deposit: Deposit, id: Long, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQL""" select confirmed_deposit(
      $id,
      ${deposit.address},
      ${deposit.txHash},
      $nodeId) """.execute()
  }

  def getUnconfirmedWithdrawalTx(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQL""" select * from get_unconfirmed_withdrawal_tx(${currency.toString}, $nodeId) """().map(row => row[Option[Long]]("id") -> row[Option[String]]("tx_hash")).head match {
      case (Some(id: Long), Some(txHash: String)) => Some(id, txHash)
      case (Some(id: Long), None) => Some(id, "")
      case _ => None
    }
  }

  def getLastConfirmedWithdrawalTx(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQL""" select * from get_last_confirmed_withdrawal_tx(${currency.toString}, $nodeId) """().map(row => row[Option[Long]]("id") -> row[Option[String]]("tx_hash")).head match {
      case (Some(id: Long), Some(txHash: String)) => Some(id, txHash)
      case _ => None
    }
  }

  def createWithdrawalTx(currency: CryptoCurrency, nodeId: Int) = DB.withConnection(db) { implicit c =>
    SQL""" select create_withdrawal_tx(${currency.toString}, $nodeId) """().map(row => row[Option[Long]]("create_withdrawal_tx")).head
  }

  def getWithdrawalTxData(txId: Long) = DB.withConnection(db) { implicit c =>
    SQL""" select * from get_withdrawal_tx_data($txId) """().map(row => row[String]("address") -> row[BigDecimal]("value")).toMap
  }

  def sentWithdrawalTx(txId: Long, txHash: String, txAmount: BigDecimal) = DB.withConnection(db) { implicit c =>
    SQL""" select sent_withdrawal_tx($txId, $txHash, $txAmount) """.execute()
  }

  def confirmedWithdrawalTx(txId: Long, txFee: BigDecimal) = DB.withConnection(db) { implicit c =>
    SQL""" select confirmed_withdrawal_tx($txId, ${txFee.bigDecimal}) """.execute()
  }

  def createColdStorageTransfer(txId: Long, address: String, value: BigDecimal) = DB.withConnection(db) { implicit c =>
    SQL""" select create_cold_storage_transfer($txId, $address, ${value.bigDecimal}) """.execute()
  }

  def getColdStorageTransfer(txId: Long) = DB.withConnection(db) { implicit c =>
    SQL""" select get_cold_storage_transfer($txId) """().map(row => row[Option[String]]("address") -> row[Option[BigDecimal]]("value")).head match {
      case (Some(address: String), Some(value: BigDecimal)) => Some(address, value)
      case _ => None
    }
  }

  def setWithdrawalTxHashMutated(txId: Long, txHash: String) = DB.withConnection(db) { implicit c =>
    SQL""" select set_withdrawal_tx_hash_mutated($txId, $txHash) """.execute()
  }

}

object WalletModel {
  case class Deposit(address: String, amount: BigDecimal, txHash: String)
}
