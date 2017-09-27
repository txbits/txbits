-- Revert txbits:2 from pg

BEGIN;
--SET ROLE txbits__owner;

drop function if exists create_user (
  a_email varchar(256),
  a_password text,
  a_onMailingList bool,
  a_pgp text,
  a_username varchar(256)
);
drop function if exists match_new(bigint, bigint, boolean, numeric(23,8), numeric(23,8), numeric(23,8), numeric(23,8)) cascade;
drop function if exists stats_new(varchar(4), varchar(4), numeric(23,8), numeric(23,8)) cascade;
drop function if exists transfer_funds(bigint, bigint, varchar(4), numeric(23,8)) cascade;
drop function if exists wallets_crypto_retire(varchar(4), integer) cascade;
drop function if exists currency_insert(varchar(4), integer) cascade;
drop function if exists withdrawal_insert(numeric(23,8), bigint, varchar(4), numeric(23,8)) cascade;
drop function if exists withdrawal_refund(numeric(23,8), bigint, varchar(4), numeric(23,8)) cascade;
drop function if exists find_user_by_email_and_password_invoker(varchar(256), text, text, inet, bool) cascade;
drop aggregate if exists first(anyelement);
drop aggregate if exists last(anyelement);
drop aggregate if exists array_agg_mult(anyarray);
drop function if exists first_agg(anyelement, anyelement);
drop function if exists last_agg(anyelement, anyelement);

-- security definer functions
drop function if exists order_new (bigint, text, varchar(4), varchar(4), numeric(23,8), numeric(23,8), boolean) cascade;
drop function if exists order_cancel (bigint, text, bigint) cascade;
drop function if exists create_user_complete (
  a_email varchar(256),
  a_password text,
  a_onMailingList bool,
  a_pgp text,
  a_token varchar(256),
  a_username varchar(256)
);
drop function if exists update_user (
   a_id bigint,
  a_email varchar(256),
  a_onMailingList bool,
  a_username varchar(256)
);
drop function if exists user_change_password (bigint, text, text) cascade;
drop function if exists trusted_action_start (
  a_email varchar(256),
  a_is_signup boolean
);
drop function if exists user_reset_password_complete (varchar(256), varchar(256), text) cascade;
drop function if exists add_api_key (bigint, text) cascade;
drop function if exists update_api_key (bigint, int, text, text, bool, bool, bool) cascade;
drop function if exists disable_api_key (bigint, int, text) cascade;
drop function if exists get_api_keys (bigint) cascade;
drop function if exists turnon_tfa (
  a_id bigint,
  a_totp int,
  a_password text
);
drop function if exists update_tfa_secret (
  a_id bigint,
  a_secret varchar(256),
  a_otps text
);
drop function if exists turnoff_tfa (
  a_id bigint,
  a_totp int,
  a_password text
);
drop function if exists user_totp_check (
  a_uid bigint,
  a_totp int
);
drop function if exists hotp (bytea, bigint) cascade;
drop function if exists base32_decode (text) cascade;
drop function if exists turnon_emails (bigint) cascade;
drop function if exists turnoff_emails (bigint) cascade;
drop function if exists add_fake_money (bigint, varchar(4), numeric(23,8)) cascade;
drop function if exists remove_fake_money (bigint, varchar(4), numeric(23,8)) cascade;
drop function if exists find_user_by_id (bigint) cascade;
drop function if exists user_exists (
  a_email varchar(256),
  out user_exists boolean
);
drop function if exists user_has_totp (
  a_email varchar(256)
);
drop function if exists user_add_pgp (bigint, text, int, text) cascade;
drop function if exists user_remove_pgp (bigint, text, int) cascade;
drop function if exists totp_login_step1 (varchar(256), text, text, inet) cascade;
drop function if exists totp_login_step2 (varchar(256), text, int, text, inet) cascade;
drop function if exists find_user_by_email_and_password (varchar(256), text, text, inet) cascade;
drop function if exists find_token (varchar(256)) cascade;
drop function if exists delete_token (varchar(256)) cascade;
drop function if exists delete_expired_tokens () cascade;
drop function if exists totp_token_is_blacklisted (bigint, bigint) cascade;
drop function if exists delete_expired_totp_blacklist_tokens () cascade;
drop function if exists new_log (bigint, text, varchar(256), text, text, inet, text) cascade;
drop function if exists login_log (bigint, timestamp(3), integer, bigint) cascade;
drop function if exists balance (bigint, text) cascade;
drop function if exists get_required_confirmations () cascade;
drop function if exists get_addresses (bigint, varchar(4)) cascade;
drop function if exists get_all_addresses (bigint) cascade;
drop function if exists user_pending_withdrawals (bigint) cascade;
drop function if exists user_pending_deposits (bigint) cascade;
drop function if exists user_pending_trades (bigint, text) cascade;
drop function if exists recent_trades (varchar(4), varchar(4)) cascade;
drop function if exists trade_history (bigint, text, timestamp(3), integer, bigint) cascade;
drop function if exists deposit_withdraw_history (bigint, timestamp(3), integer, bigint) cascade;
drop function if exists open_asks (varchar(4), varchar(4)) cascade;
drop function if exists open_bids (varchar(4), varchar(4)) cascade;
drop function if exists orders_depth (varchar(4), varchar(4)) cascade;
drop function if exists get_recent_matches (timestamp(3)) cascade;
drop function if exists get_currencies () cascade;
drop function if exists dw_fees () cascade;
drop function if exists trade_fees () cascade;
drop function if exists dw_limits () cascade;
drop function if exists get_pairs () cascade;
drop function if exists chart_from_db (varchar(4), varchar(4)) cascade;
drop function if exists withdraw_crypto (
  a_uid bigint,
  a_amount numeric(23,8),
  a_address varchar(35),
  a_currency varchar(4),
  a_tfa_code int
);
COMMIT;

-- vi: expandtab ts=2 sw=2
