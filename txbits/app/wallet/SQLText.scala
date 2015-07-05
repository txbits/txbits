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

object SQLText {

  val obtainSessionLock = SQL(
    """
      |select pg_try_advisory_lock({currency}, {node_id})
    """.stripMargin)

  val addNewAddress = SQL(
    """
    |insert into users_addresses (address, currency, node_id)
    |values({address}, {currency}, {node_id})
  """.stripMargin)

  val getFreeAddressCount = SQL(
    """
      |select free_address_count({currency}, {node_id})
    """.stripMargin)

  val getMinConfirmations = SQL(
    """
      |select * from get_min_confirmations({currency})
    """.stripMargin)

  val getNodeInfo = SQL(
    """
      |select * from get_node_info({currency}, {node_id})
    """.stripMargin)

  val getBalance = SQL(
    """
      |select get_balance({currency}, {node_id})
    """.stripMargin)

  val getLastBlockRead = SQL(
    """
      |select * from get_last_block_read({currency}, {node_id})
    """.stripMargin)

  val setLastBlockRead = SQL(
    """
      |select set_last_block_read({currency}, {node_id},
      |{block_count}, {last_withdrawal_time_received})
    """.stripMargin)

  val createDeposit = SQL(
    """
      |select create_deposit({currency}, {node_id}, {address}, {amount}, {tx_hash})
    """.stripMargin)

  val createConfirmedDeposit = SQL(
    """
      |select create_confirmed_deposit({currency}, {node_id}, {address}, {amount}, {tx_hash})
    """.stripMargin)

  val isConfirmedDeposit = SQL(
    """
      |select is_confirmed_deposit({address}, {amount}, {tx_hash})
    """.stripMargin)

  val getPendingDeposits = SQL(
    """
      |select * from get_pending_deposits({currency}, {node_id})
    """.stripMargin)

  val confirmedDeposit = SQL(
    """
      |select confirmed_deposit({id}, {address}, {tx_hash}, {node_id})
    """.stripMargin)

  val getUnconfirmedWithdrawalTx = SQL(
    """
      |select * from get_unconfirmed_withdrawal_tx({currency}, {node_id})
    """.stripMargin)

  val createWithdrawalTx = SQL(
    """
      |select create_withdrawal_tx({currency}, {node_id})
    """.stripMargin)

  val getWithdrawalTx = SQL(
    """
      |select * from get_withdrawal_tx({tx_id})
    """.stripMargin)

  val sentWithdrawalTx = SQL(
    """
      |select sent_withdrawal_tx({tx_id}, {tx_hash}, {tx_amount})
    """.stripMargin)

  val confirmedWithdrawalTx = SQL(
    """
      |select confirmed_withdrawal_tx({tx_id}, {tx_fee})
    """.stripMargin)

  val createColdStorageTransfer = SQL(
    """
      |select create_cold_storage_transfer({tx_id}, {address}, {value})
    """.stripMargin)

  val setWithdrawalTxHashMutated = SQL(
    """
      |select set_withdrawal_tx_hash_mutated({tx_id}, {tx_hash})
    """.stripMargin)

}
