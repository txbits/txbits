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
      |values ({address}, {currency}, {node_id})
    """.stripMargin)

  val getFreeAddressCount = SQL(
    """
      |select count(1) from users_addresses
      |where assigned is NULL and user_id = 0 and
      |currency = {currency} and node_id = {node_id}
    """.stripMargin)

  val getMinConfirmations = SQL(
    """
      |select active, min_deposit_confirmations, min_withdrawal_confirmations
      |from currencies_crypto where currency = {currency}
    """.stripMargin)

  val getNodeInfo = SQL(
    """
      |select retired, balance_min, balance_warn, balance_target, balance_max
      |from wallets_crypto where currency = {currency} and
      |node_id = {node_id}
    """.stripMargin)

  val getLastBlockRead = SQL(
    """
      |select last_block_read, last_withdrawal_time_received from wallets_crypto
      |where currency = {currency} and node_id = {node_id}
    """.stripMargin)

  val setLastBlockRead = SQL(
    """
      |update wallets_crypto set last_block_read = {block_count},
      |last_withdrawal_time_received = {last_withdrawal_time_received},
      |balance = {balance} where currency = {currency} and
      |node_id = {node_id}
    """.stripMargin)

  val createDeposit = SQL(
    """
      with null_rows as (
        update users_addresses set assigned = current_timestamp
        where assigned is NULL and user_id = 0 and currency = {currency} and node_id = {node_id} and address = {address}
        returning user_id
      ), zero_rows as (
        insert into users_addresses (currency, node_id, address, assigned)
        select {currency}, {node_id}, {address}, current_timestamp where not exists
          (select 1 from null_rows
            union
           select 1 from users_addresses
            where assigned is not NULL and currency = {currency} and node_id = {node_id} and address = {address}
          ) returning user_id
      ), rows as (
        insert into deposits (amount, user_id, currency, fee)
          values (
            {amount},
            (
              select user_id from null_rows
               union
              select user_id from zero_rows
               union
              select user_id from users_addresses
               where assigned is not NULL and currency = {currency} and node_id = {node_id} and address = {address}
            ),
            {currency},
            (
              select deposit_constant + {amount} * deposit_linear from dw_fees where currency = {currency} and method = 'blockchain'
            )
          ) returning id
      )
      insert into deposits_crypto (id, tx_hash, address)
        values ((select id from rows), {tx_hash}, {address}) returning id
    """)

  val createConfirmedDeposit = SQL(
    """
      with null_rows as (
        update users_addresses set assigned = current_timestamp
        where assigned is NULL and user_id = 0 and currency = {currency} and node_id = {node_id} and address = {address}
        returning user_id
      ), zero_rows as (
        insert into users_addresses (currency, node_id, address, assigned)
        select {currency}, {node_id}, {address}, current_timestamp where not exists
          (select 1 from null_rows
            union
           select 1 from users_addresses
            where assigned is not NULL and currency = {currency} and node_id = {node_id} and address = {address}
          ) returning user_id
      ), rows as (
        insert into deposits (amount, user_id, currency, fee)
          values (
            {amount},
            (
              select user_id from null_rows
               union
              select user_id from zero_rows
               union
              select user_id from users_addresses
               where assigned is not NULL and currency = {currency} and node_id = {node_id} and address = {address}
            ),
            {currency},
            (
              select deposit_constant + {amount} * deposit_linear from dw_fees where currency = {currency} and method = 'blockchain'
            )
          ) returning id
      )
      insert into deposits_crypto (id, tx_hash, address, confirmed)
        values ((select id from rows), {tx_hash}, {address}, current_timestamp)
    """)

  val isConfirmedDeposit = SQL(
    """
      |select exists (select 1 from deposits_crypto where
      |address = {address} and tx_hash = {tx_hash} and
      |confirmed is not NULL) as exists
    """.stripMargin)

  val getPendingDeposits = SQL(
    """
      |select d.id, d.amount, dc.tx_hash, dc.address
      |from deposits d inner join deposits_crypto dc on d.id = dc.id
      |inner join users_addresses a on a.address = dc.address and
      |a.user_id = d.user_id and a.currency = d.currency
      |where d.currency = {currency} and
      |node_id = {node_id} and confirmed is NULL
    """.stripMargin)

  val confirmedDeposit = SQL(
    """
      |update deposits_crypto set confirmed = current_timestamp
      |where id = {id} and address = {address} and
      |tx_hash = {tx_hash} and confirmed is NULL
    """.stripMargin)

  val getUnconfirmedWithdrawalTx = SQL(
    """
      |select id, tx_hash from withdrawals_crypto_tx
      |where id = (select max(id) from withdrawals_crypto_tx
      |where currency = {currency} and node_id = {node_id}) and
      |sent is not NULL and confirmed is NULL
    """.stripMargin)

  val createWithdrawalTx = SQL(
    """
      |with rows as (
      |insert into withdrawals_crypto_tx (currency, node_id)
      |select {currency}, {node_id} where exists (select w.id
      |from withdrawals w inner join withdrawals_crypto wc on w.id = wc.id
      |where currency = {currency} and withdrawals_crypto_tx_id
      |is NULL) returning id
      |)
      |update withdrawals_crypto
      |set withdrawals_crypto_tx_id = (select id from rows)
      |where exists (select id from rows) and
      |withdrawals_crypto_tx_id is NULL and
      |id = any (select w.id
      |from withdrawals w inner join withdrawals_crypto wc on w.id = wc.id
      |where currency = {currency} and
      |withdrawals_crypto_tx_id is NULL)
      |returning withdrawals_crypto_tx_id
    """.stripMargin)

  val getWithdrawalTx = SQL(
    """
      |select address, sum(amount - fee) as value
      |from withdrawals w inner join withdrawals_crypto wc on w.id = wc.id
      |where withdrawals_crypto_tx_id = {tx_id} group by address
    """.stripMargin)

  val sentWithdrawalTx = SQL(
    """
      |update withdrawals_crypto_tx set sent = current_timestamp,
      |tx_hash = {tx_hash} where id = {tx_id} and sent is NULL
    """.stripMargin)

  val confirmedWithdrawalTx = SQL(
    """
      |update withdrawals_crypto_tx set confirmed = current_timestamp,
      |tx_fee = {tx_fee} where id = {tx_id} and confirmed is NULL
    """.stripMargin)

  val createColdStorageTransfer = SQL(
    """
      |insert into withdrawals_crypto_tx_cold_storage (id, address, value)
      |values ({tx_id}, {address}, {value})
    """.stripMargin)

  val setWithdrawalTxHashMutated = SQL(
    """
      |insert into withdrawals_crypto_tx_mutated (id, tx_hash_mutated)
      |values ({tx_id}, {tx_hash})
    """.stripMargin)

}
