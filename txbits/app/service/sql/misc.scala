// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package service.sql

import anorm._

object misc {

  val userClean = SQL(
    """
      |begin;
      |delete from balances;
      |delete from users_addresses;
      |delete from users_tfa_secrets;
      |delete from users_backup_otps;
      |delete from users_passwords;
      |delete from users;
      |commit;
    """.stripMargin)

  val cleanForTest = SQL(
    """
      |delete from deposits_crypto;
      |delete from deposits_other;
      |delete from deposits;
      |delete from users_passwords;
      |delete from users_tfa_secrets;
      |delete from users_backup_otps;
      |delete from users_addresses;
      |delete from dw_fees;
      |delete from trade_fees;
      |delete from totp_tokens_blacklist;
      |delete from withdrawals_other;
      |delete from withdrawals_crypto;
      |delete from withdrawals_crypto_tx_mutated;
      |delete from withdrawals_crypto_tx_cold_storage;
      |delete from withdrawals_crypto_tx;
      |delete from withdrawals;
      |delete from currencies_crypto;
      |delete from wallets_crypto;
      |delete from balances;
      |delete from matches;
      |delete from orders;
      |delete from markets;
      |delete from withdrawal_limits;
      |delete from currencies;
      |delete from users;
      |""".stripMargin)

  val setupForTest = SQL(
    """
      |
      |select currency_insert('BTC',10);
      |select currency_insert('LTC',20);
      |select currency_insert('USD',30);
      |select currency_insert('CAD',40);
      |
      |insert into markets(base,counter,limit_min,position) values('BTC','USD',0.01,10);
      |insert into markets(base,counter,limit_min,position) values('LTC','USD',0.1,20);
      |insert into markets(base,counter,limit_min,position) values('LTC','BTC',0.1,30);
      |insert into markets(base,counter,limit_min,position) values('USD','CAD',1.00,40);
      |
      |insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('LTC', 'blockchain', 0.000, 0.000, 0.000, 0.000);
      |insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('BTC', 'blockchain', 0.000, 0.000, 0.000, 0.000);
      |insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('USD', 'wire', 0.000, 0.000, 0.000, 0.000);
      |insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('CAD', 'wire', 0.000, 0.000, 0.000, 0.000);
      |
      |insert into trade_fees(linear) values (0.005);
      |
      |insert into withdrawal_limits(currency, limit_min, limit_max) values ('LTC', 0.001, 100);
      |insert into withdrawal_limits(currency, limit_min, limit_max) values ('BTC', 0.001, 100);
      |insert into withdrawal_limits(currency, limit_min, limit_max) values ('USD', 1, 10000);
      |insert into withdrawal_limits(currency, limit_min, limit_max) values ('CAD', 1, 10000);
      |
      |insert into currencies_crypto(currency) values('BTC');
      |insert into currencies_crypto(currency) values('LTC');
      |
      |insert into wallets_crypto(currency, last_block_read, balance_min, balance_warn, balance_target, balance_max) values('LTC', 42, 0, 0, 1000, 10000);
      |insert into wallets_crypto(currency, last_block_read, balance_min, balance_warn, balance_target, balance_max) values('BTC', 42, 0, 0, 100, 1000);
      |
      |insert into users(id, email) values (0, '');
      |insert into balances (user_id, currency) select 0, currency from currencies;
    """.stripMargin
  )

  val setFees = SQL(
    """
      |update dw_fees set deposit_constant = {depositConstant}, deposit_linear = {depositLinear}, withdraw_constant = {withdrawConstant}, withdraw_linear = {withdrawLinear} where currency = {currency} and method = {method};
    """.stripMargin)

  val metaClean = SQL(
    """
      |delete from constants;
      |delete from currencies; -- after balances
      |delete from markets; -- after currencies, orders
    """.stripMargin)

}
