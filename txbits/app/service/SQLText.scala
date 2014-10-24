// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package service

import anorm._

object SQLText {

  val userClean = SQL(
    """
      |begin;
      |delete from balances;
      |delete from users_addresses;
      |delete from users;
      |commit;
    """.stripMargin)

  val specialUserCreate = SQL(
    """
      |insert into users(id, email, password, hasher) values (0, '','','');
    """.stripMargin)

  val createUser = SQL(
    """
      |insert into users (email, password, hasher, on_mailing_list)
      |values ({email},{password},{hasher},{onMailingList})
    """.stripMargin)

  val updateUser = SQL(
    """
      |update users set email={email}, password={password}, hasher={hasher}, on_mailing_list={onMailingList}
      |where id={id}
    """.stripMargin)

  val turnonTfa = SQL(
    """
      |update users set tfa_login=true, tfa_withdrawal=true
      |where id={id}
    """.stripMargin)

  val updateTfaSecret = SQL(
    """
      |update users set tfa_secret={secret}, tfa_type={typ}, tfa_login=false, tfa_withdrawal=false
      |where id={id}
    """.stripMargin)

  val turnoffTfa = SQL(
    """
      |update users set tfa_secret=NULL, tfa_login=false, tfa_withdrawal=false, tfa_type=NULL
      |where id={id}
    """.stripMargin)

  val turnonEmails = SQL(
    """
      |update users set on_mailing_list=true
      |where id={id}
    """.stripMargin)

  val turnoffEmails = SQL(
    """
      |update users set on_mailing_list=false
      |where id={id}
    """.stripMargin)

  val addFakeMoney = SQL(
    """
      |insert into transactions(to_user_id, currency, amount, type)
      |values ({uid},{currency},{amount},'X')
    """.stripMargin)

  val removeFakeMoney = SQL(
    """
      |insert into transactions(from_user_id, currency, amount, type)
      |values ({uid},{currency},{amount},'X')
    """.stripMargin)

  val findUserById = SQL(
    """
      |select email, hasher, verification, on_mailing_list,
      |password, id, tfa_withdrawal, tfa_login, tfa_secret, tfa_type from users where id = {id}
    """.stripMargin)

  val findUserByEmail = SQL(
    """
      |select email, hasher, verification, on_mailing_list,
      |password, id, tfa_withdrawal, tfa_login, tfa_secret, tfa_type from users where lower(email) = lower({email})
    """.stripMargin)

  val saveToken = SQL(
    """
      |insert into tokens (email, token, creation, expiration, is_signup)
      |values ({email},{token},{creation},{expiration},{is_signup})
    """.stripMargin)

  val findToken = SQL(
    """
      |select email, token, creation, expiration, is_signup from tokens where token = {token}
    """.stripMargin)

  val deleteToken = SQL(
    """
      |delete from tokens where token = {token}
    """.stripMargin)

  val deleteExpiredTokens = SQL(
    """
      |delete from tokens where expiration < current_timestamp
    """.stripMargin)

  val TOTPTokenIsBlacklisted = SQL(
    """
      |select true from totp_tokens_blacklist where user_id = {user} and token = {token} and expiration >= current_timestamp
    """.stripMargin)

  val blacklistTOTPToken = SQL(
    """
      |insert into totp_tokens_blacklist(user_id, token, expiration) values ({user}, {token}, {expiration})
    """.stripMargin)

  val deleteExpiredTOTPBlacklistTokens = SQL(
    """
      |delete from totp_tokens_blacklist where expiration < current_timestamp
    """.stripMargin)

  val newLog = SQL(
    """
      |insert into event_log (user_id, email, ipv4, browser_headers, browser_id, ssl_info, type)
      |values ({user_id}, {email}, {ipv4}, {browser_headers}, {browser_id}, {ssl_info}, {type})
    """.stripMargin)

  val loginLog = SQL(
    """
      |select user_id, created, email, ipv4, ipv6, browser_headers, browser_id, ssl_info, type
      |from event_log where type in ('login_success', 'login_failure', 'logout', 'session_expired') and user_id = {user_id}
      |order by created desc
    """.stripMargin)

  val cleanForTest = SQL(
    """
      |delete from deposits_crypto;
      |delete from deposits;
      |delete from users_addresses;
      |delete from dw_fees;
      |delete from trade_fees;
      |delete from transactions;
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
      |insert into currencies(currency) values('LTC');
      |insert into currencies(currency) values('BTC');
      |insert into currencies(currency) values('USD');
      |insert into currencies(currency) values('CAD');
      |
      |insert into markets(base,counter,limit_min) values('LTC','USD',0.1);
      |insert into markets(base,counter,limit_min) values('LTC','BTC',0.1);
      |insert into markets(base,counter,limit_min) values('BTC','USD',0.01);
      |insert into markets(base,counter,limit_min) values('USD','CAD',1.00);
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
      |insert into users(id, email, password, hasher) values (0, '','','');
    """.stripMargin
  )

  val setFees = SQL(
    """
      |update dw_fees set deposit_constant = {depositConstant}, deposit_linear = {depositLinear}, withdraw_constant = {withdrawConstant}, withdraw_linear = {withdrawLinear} where currency = {currency} and method = {method};
    """.stripMargin)

  val balance = SQL(
    """
      |select c.currency, coalesce(b.balance, 0) as amount, hold from currencies c
      |left outer join balances b on c.currency = b.currency and user_id = {uid}
      |""".stripMargin)

  val getRequiredConfirmations = SQL(
    """
      |select currency, min_deposit_confirmations
      |from currencies_crypto where active = true
    """.stripMargin)

  val getAddresses = SQL(
    """
      |with rows as (
      |update users_addresses set user_id = {uid}, assigned = current_timestamp
      |where assigned is NULL and user_id = 0 and currency = {currency} and
      |address = (select address from users_addresses
      |where assigned is NULL and user_id = 0 and currency = {currency} limit 1)
      |and not exists (select 1 from (select address from users_addresses
      |where user_id = {uid} and currency = {currency} order by assigned desc limit 1)
      |a left join deposits_crypto dc on dc.address = a.address where dc.id is NULL)
      |returning address, assigned
      |)
      |(select address, assigned from rows)
      |union
      |(select address, assigned from users_addresses
      |where user_id = {uid} and currency = {currency})
      |order by assigned desc
      |""".stripMargin)

  val getAllAddresses = SQL(
    """
      |with rows as (
      |update users_addresses set user_id = {uid}, assigned = current_timestamp
      |where assigned is NULL and user_id = 0 and
      |address = any (select distinct on (currency) address from users_addresses
      |where assigned is NULL and user_id = 0 and currency not in (select currency
      |from (select distinct on (currency) currency, address from users_addresses
      |where user_id = {uid} and currency = any (select currency
      |from currencies_crypto where active = true) order by currency, assigned desc)
      |a left join deposits_crypto dc on dc.address = a.address where dc.id is NULL))
      |returning currency, address, assigned
      |)
      |(select currency, address, assigned from rows)
      |union
      |(select currency, address, assigned from users_addresses
      |where user_id = {uid} and currency = any (select currency
      |from currencies_crypto where active = true))
      |order by assigned desc
      |""".stripMargin)

  val getAllWithdrawals = SQL(
    """
      |select currency, amount, fee, created, address as info
      |from withdrawals_crypto wc
      |inner join withdrawals w on w.id = wc.id
      |where w.user_id = {uid} and withdrawals_crypto_tx_id is null
      |order by created desc
      |""".stripMargin)

  val getAllDeposits = SQL(
    """
      |select currency, amount, fee, created, address as info
      |from deposits_crypto dc
      |inner join deposits d on d.id = dc.id
      |where d.user_id = {uid} and dc.confirmed is null
      |order by created desc
      |""".stripMargin)

  val askBid = SQL(
    """
      |select order_new({uid}, {base}, {counter}, {amount}, {price}, {is_bid})
      |""".stripMargin)

  val cancelTrade = SQL(
    """
      |select order_cancel({id}, {uid})
    """.stripMargin)

  val userPendingTrades = SQL(
    """
      |select id, is_bid, remains as amount, price, base, counter, created from orders
      |where user_id = {uid} and closed = false and remains > 0
      |order by created desc
      |""".stripMargin)

  val recentTrades = SQL(
    """
      |select amount, created, price, base, counter, is_bid
      |from matches m
      |where base = {base} and counter = {counter}
      |order by created desc
      |limit 40
      |""".stripMargin)

  val tradeHistory = SQL(
    """
      |select amount, created, price, base, counter, type, fee from (
        |select amount, created, price, base, counter, 'bid' as type, bid_fee as fee
        |from matches where bid_user_id = {id}
        |union
        |select amount, created, price, base, counter, 'ask' as type, ask_fee as fee
        |from matches where ask_user_id = {id}
      |) as trade_history order by created desc
      |""".stripMargin)

  // TODO: get more info about deposits and withdrawals depending on their type (crypto/fiat)
  val depositWithdrawHistory = SQL(
    """
      |select * from
      |(
        |(
          |select amount, created, currency, fee, 'w' as type, address
          |from withdrawals w left join withdrawals_crypto wc on w.id = wc.id
          |where user_id = {id}
        |)
        |union
        |(
          |select amount, created, currency, fee, 'd' as type, address
          |from deposits d left join deposits_crypto dc on d.id = dc.id
          |where user_id = {id} and (dc.confirmed is not null or dc.id is null)
        |)
      |) as a
      |order by created desc
      |""".stripMargin)

  val openAsks = SQL(
    """
      |select sum(remains) as amount, price, base, counter from orders
      |where not is_bid and base = {base} and counter = {counter} and closed = false and remains > 0
      |group by price, base, counter order by price asc limit 40
      |""".stripMargin)

  val openBids = SQL(
    """
      |select sum(remains) as amount, price, base, counter from orders
      |where is_bid and base = {base} and counter = {counter} and closed = false and remains > 0
      |group by price, base, counter order by price desc limit 40
      |""".stripMargin)

  val getRecentMatches = SQL(
    """
      |select amount, m.created, m.price, m.base, m.counter
      |from matches m
      |where m.created > {last_match}
      |order by m.created desc
      |""".stripMargin)

  val getCurrencies = SQL(
    """
      |select * from currencies
      |""".stripMargin)

  val dwFees = SQL(
    """
      |select * from dw_fees
      |""".stripMargin)

  val tradeFees = SQL(
    """
      |select * from trade_fees
      |""".stripMargin)

  val dwLimits = SQL(
    """
      |select * from withdrawal_limits
      |""".stripMargin)

  val getPairs = SQL(
    """
      |select * from markets
      |""".stripMargin)

  val metaClean = SQL(
    """
      |delete from constants;
      |delete from currencies; -- after balances
      |delete from markets; -- after currencies, orders
    """.stripMargin)

  val withdrawCrypto = SQL(
    """
      |with rows as (
      |insert into withdrawals (amount, user_id, currency, fee)
      |values ({amount}, {uid}, {currency}, (select withdraw_constant + {amount} * withdraw_linear from dw_fees where currency = {currency} and method = 'blockchain')) returning id
      |)
      |insert into withdrawals_crypto (id, address)
      |values ((select id from rows), {address})
    """.stripMargin)

}
