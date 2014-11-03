// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package service.sql

import anorm._

object frontend {
  val createUser = SQL(
    """
    | select * from create_user({password}, {email}, {onMailingList})
    |""".stripMargin)

  val updateUser = SQL(
    """
    | select * from update_user({id}, {email}, {onMailingList})
    |""".stripMargin)

  val userChangePassword = SQL(
    """
    | select * from user_change_password({user_id}, {password})
    |""".stripMargin)

  val turnonTfa = SQL(
    """
    | select * from turnon_tfa({id})
    |""".stripMargin)

  val updateTfaSecret = SQL(
    """
    | select * from update_tfa_secret({id}, {secret}, {typ})
    |""".stripMargin)

  val turnoffTfa = SQL(
    """
    | select * from turnoff_tfa({id})
    |""".stripMargin)

  val turnonEmails = SQL(
    """
    | select * from turnon_emails({id})
    |""".stripMargin)

  val turnoffEmails = SQL(
    """
    | select * from turnoff_emails({id})
    |""".stripMargin)

  val addFakeMoney = SQL(
    """
    | select * from add_fake_money({uid}, {currency}, {amount})
    |""".stripMargin)

  val removeFakeMoney = SQL(
    """
    | select * from remove_fake_money({uid}, {currency}, {amount})
    |""".stripMargin)

  val findUserById = SQL(
    """
    | select * from find_user_by_id({id})
    |""".stripMargin)

  val findUserByEmail = SQL(
    """
    | select * from find_user_by_email({email})
    |""".stripMargin)

  val findUserByEmailAndPassword = SQL(
    """
    | select * from find_user_by_email_and_password({email}, {password})
    |""".stripMargin)

  val saveToken = SQL(
    """
    | select * from save_token({token}, {email}, {is_signup}, {creation}, {expiration})
    |""".stripMargin)

  val findToken = SQL(
    """
    | select * from find_token({token})
    |""".stripMargin)

  val deleteToken = SQL(
    """
    | select * from delete_token({token})
    |""".stripMargin)

  val deleteExpiredTokens = SQL(
    """
    | select * from delete_expired_tokens()
    |""".stripMargin)

  val TOTPTokenIsBlacklisted = SQL(
    """
    | select * from totp_token_is_blacklisted({token}, {user})
    |""".stripMargin)

  val blacklistTOTPToken = SQL(
    """
    | select * from blacklist_totp_token({token}, {user}, {expiration})
    |""".stripMargin)

  val deleteExpiredTOTPBlacklistTokens = SQL(
    """
    | select * from delete_expired_totp_blacklist_tokens()
    |""".stripMargin)

  val newLog = SQL(
    """
    | select * from new_log({user_id}, {browser_headers}, {email}, {ssl_info}, {browser_id}, {ipv4}, {type})
    |""".stripMargin)

  val loginLog = SQL(
    """
    | select * from login_log({user_id})
    |""".stripMargin)

  val balance = SQL(
    """
    | select * from balance({uid})
    |""".stripMargin)

  val getRequiredConfirmations = SQL(
    """
    | select * from get_required_confirmations()
    |""".stripMargin)

  val getAddresses = SQL(
    """
    | select * from get_addresses({uid}, {currency})
    |""".stripMargin)

  val getAllAddresses = SQL(
    """
    | select * from get_all_addresses({uid})
    |""".stripMargin)

  val getAllWithdrawals = SQL(
    """
    | select * from get_all_withdrawals({uid})
    |""".stripMargin)

  val getAllDeposits = SQL(
    """
    | select * from get_all_deposits({uid})
    |""".stripMargin)

  val orderNew = SQL(
    """
    | select order_new({uid}, {base}, {counter}, {amount}, {price}, {is_bid})
    |""".stripMargin)

  val orderCancel = SQL(
    """
    | select * from order_cancel({id}, {uid})
    |""".stripMargin)

  val userPendingTrades = SQL(
    """
    | select * from user_pending_trades({uid})
    |""".stripMargin)

  val recentTrades = SQL(
    """
    | select * from recent_trades({base}, {counter})
    |""".stripMargin)

  val tradeHistory = SQL(
    """
    | select * from trade_history({id})
    |""".stripMargin)

  val depositWithdrawHistory = SQL(
    """
    | select * from deposit_withdraw_history({id})
    |""".stripMargin)

  val openOrders = SQL(
    """
    | select * from open_orders({base}, {counter})
    |""".stripMargin)

  val getRecentMatches = SQL(
    """
    | select * from get_recent_matches({last_match})
    |""".stripMargin)

  val getCurrencies = SQL(
    """
    | select * from get_currencies()
    |""".stripMargin)

  val dwFees = SQL(
    """
    | select * from dw_fees()
    |""".stripMargin)

  val tradeFees = SQL(
    """
    | select * from trade_fees()
    |""".stripMargin)

  val dwLimits = SQL(
    """
    | select * from dw_limits()
    |""".stripMargin)

  val getPairs = SQL(
    """
    | select * from get_pairs()
    |""".stripMargin)

  val chartFromDb = SQL(
    """
    | select * from chart_from_db({base}, {counter})
    |""".stripMargin)

  val withdrawCrypto = SQL(
    """
    | select * from withdraw_crypto({uid}, {amount}, {address}, {currency})
    |""".stripMargin)
}

