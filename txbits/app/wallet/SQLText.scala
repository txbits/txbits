// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

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

  val getLastBlockRead = SQL(
    """
      |select * from get_last_block_read({currency}, {node_id})
    """.stripMargin)

  val setLastBlockRead = SQL(
    """
      |select set_last_block_read({currency}, {node_id},
      |{block_count}, {last_withdrawal_time_received}, {balance})
    """.stripMargin)

  val createDeposit = SQL(
    """
      |select create_deposit({currency}, {node_id}, {address}, {amount}, {tx_hash}, {fee})
    """.stripMargin)

  val createConfirmedDeposit = SQL(
    """
      |select create_confirmed_deposit({currency}, {node_id}, {address}, {amount}, {tx_hash}, {fee})
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
      |select confirmed_deposit({id}, {address}, {tx_hash})
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
      |select sent_withdrawal_tx({tx_id}, {tx_hash})
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
