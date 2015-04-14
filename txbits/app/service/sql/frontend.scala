// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package service.sql

import anorm._

object frontend {

  // This function requires superuser database permissions
  val createUserInsecure = SQL(
    """
    | select create_user as id from create_user({email}, {password}, {onMailingList}, null)
    |""".stripMargin)

  val createUserComplete = SQL(
    """
    | select create_user_complete as id from create_user_complete({email}, {password}, {onMailingList}, {pgp}, {token})
    |""".stripMargin)

  val updateUser = SQL(
    """
    | select * from update_user({id}, {email}, {onMailingList})
    |""".stripMargin)

  val userExists = SQL(
    """
    | select * from user_exists({email});
    |""".stripMargin)

  val userHasTotp = SQL(
    """
    | select * from user_has_totp({email});
    """.stripMargin)

  val userAddPgp = SQL(
    """
    | select user_add_pgp as success from user_add_pgp({id}, {password}, {tfa_code}, {pgp});
    |""".stripMargin)

  val userRemovePgp = SQL(
    """
    | select user_remove_pgp as success from user_remove_pgp({id}, {password}, {tfa_code});
    |""".stripMargin)

  val userAddApiKey = SQL(
    """
    | select add_api_key({uid}, {api_key})
    """.stripMargin)

  val userUpdateApiKey = SQL(
    """
    | select update_api_key as success from update_api_key({uid}, {tfa_code}, {api_key}, {comment}, {trading}, {trade_history}, {list_balance})
    """.stripMargin)

  val userDisableApiKey = SQL(
    """
    | select disable_api_key as success from disable_api_key({uid}, {tfa_code}, {api_key})
    """.stripMargin)

  val userGetApiKeys = SQL(
    """
    | select * from get_api_keys({uid})
    """.stripMargin)

  val userChangePassword = SQL(
    """
    | select user_change_password({user_id}, {old_password}, {new_password})
    |""".stripMargin)

  val userResetPasswordComplete = SQL(
    """
    | select user_reset_password_complete as success from user_reset_password_complete({email}, {token}, {password})
    |""".stripMargin)

  val trustedActionStart = SQL(
    """
    | select trusted_action_start as success from trusted_action_start({email}, {is_signup})
    |""".stripMargin)

  val confirmWithdrawal = SQL(
    """
    | select confirm_withdrawal as success from confirm_withdrawal({id}, {token})
    |""".stripMargin)

  val rejectWithdrawal = SQL(
    """
    | select reject_withdrawal as success from reject_withdrawal({id}, {token})
    |""".stripMargin)

  val turnonTfa = SQL(
    """
    | select turnon_tfa as success from turnon_tfa({id}, {tfa_code}, {password})
    |""".stripMargin)

  val updateTfaSecret = SQL(
    """
    | select update_tfa_secret as success from update_tfa_secret({id}, {secret}, {otps})
    |""".stripMargin)

  val turnoffTfa = SQL(
    """
    | select turnoff_tfa as success from turnoff_tfa({id}, {tfa_code}, {password})
    |""".stripMargin)

  val turnonEmails = SQL(
    """
    | select turnon_emails as success from turnon_emails({id})
    |""".stripMargin)

  val turnoffEmails = SQL(
    """
    | select turnoff_emails as success from turnoff_emails({id})
    |""".stripMargin)

  val userPgpByEmail = SQL(
    """
    | select * from user_pgp_by_email({email})
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

  val findUserByEmailAndPassword = SQL(
    """
    | select id, email, verification, on_mailing_list, tfa_enabled, pgp, active
    | from find_user_by_email_and_password({email}, {password}, {browser_headers}, inet({ip}))
    |""".stripMargin)

  val totpLoginStep1 = SQL(
    """
    | select totp_login_step1({email}, {password}, {browser_headers}, inet({ip}))
    |""".stripMargin)

  val totpLoginStep2 = SQL(
    """
    | select * from totp_login_step2({email}, {totp_hash}, {totp_token}, {browser_headers}, inet({ip}))
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

  val deleteExpiredTOTPBlacklistTokens = SQL(
    """
    | select * from delete_expired_totp_blacklist_tokens()
    |""".stripMargin)

  val newLog = SQL(
    """
    | select * from new_log({user_id}, {browser_headers}, {email}, {ssl_info}, {browser_id}, inet({ip}), {type})
    |""".stripMargin)

  val loginLog = SQL(
    """
    | select * from login_log({user_id})
    |""".stripMargin)

  val apiBalance = SQL(
    """
    | select * from balance(null, {api_key})
    |""".stripMargin)

  val balance = SQL(
    """
    | select * from balance({uid}, null)
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

  val apiOrderNew = SQL(
    """
    | select order_new(null, {api_key}, {base}, {counter}, {amount}, {price}, {is_bid})
    |""".stripMargin)

  val orderNew = SQL(
    """
    | select order_new({uid}, null, {base}, {counter}, {amount}, {price}, {is_bid})
    |""".stripMargin)

  val apiOrderCancel = SQL(
    """
    | select * from order_cancel(null, {api_key}, {id})
    |""".stripMargin)

  val orderCancel = SQL(
    """
    | select * from order_cancel({uid}, null, {id})
    |""".stripMargin)

  val apiUserPendingTrades = SQL(
    """
    | select * from user_pending_trades(null, {api_key})
    |""".stripMargin)

  val userPendingTrades = SQL(
    """
    | select * from user_pending_trades({uid}, null)
    |""".stripMargin)

  val recentTrades = SQL(
    """
    | select * from recent_trades({base}, {counter})
    |""".stripMargin)

  val apiTradeHistory = SQL(
    """
    | select * from trade_history(null, {api_key})
    |""".stripMargin)

  val tradeHistory = SQL(
    """
    | select * from trade_history({id}, null)
    |""".stripMargin)

  val depositWithdrawHistory = SQL(
    """
    | select * from deposit_withdraw_history({id})
    |""".stripMargin)

  val openAsks = SQL(
    """
    | select * from open_asks({base}, {counter})
    |""".stripMargin)

  val openBids = SQL(
    """
    | select * from open_bids({base}, {counter})
    |""".stripMargin)

  val ordersDepth = SQL(
    """
    | select * from orders_depth({base}, {counter})
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
    | select withdraw_crypto as id from withdraw_crypto({uid}, {amount}, {address}, {currency}, {tfa_code})
    |""".stripMargin)
}

