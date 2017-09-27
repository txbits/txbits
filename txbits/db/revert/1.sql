-- Revert txbits:1 from pg

BEGIN;
--SET ROLE txbits__owner;

-- Squelch notices
SET LOCAL client_min_messages = WARNING;

drop table if exists balances cascade;
drop table if exists orders cascade;
drop table if exists currencies cascade;
drop table if exists deposits cascade;
drop table if exists deposits_crypto cascade;
drop table if exists deposits_other cascade;
drop table if exists matches cascade;
drop table if exists stats_30_min cascade;
drop table if exists markets cascade;
drop table if exists tokens cascade;
drop table if exists users cascade;
drop table if exists users_passwords cascade;
drop table if exists users_api_keys cascade;
drop table if exists users_backup_otps cascade;
drop table if exists users_tfa_secrets cascade;
drop table if exists users_addresses cascade;
drop table if exists withdrawals cascade;
drop table if exists withdrawals_other cascade;
drop table if exists withdrawals_crypto cascade;
drop table if exists withdrawals_crypto_tx cascade;
drop table if exists withdrawals_crypto_tx_cold_storage cascade;
drop table if exists withdrawals_crypto_tx_mutated cascade;
drop table if exists currencies_crypto cascade;
drop table if exists wallets_crypto cascade;
drop table if exists dw_fees cascade;
drop table if exists trade_fees cascade;
drop table if exists totp_tokens_blacklist cascade;
drop table if exists event_log cascade;
drop table if exists withdrawal_limits cascade;
drop table if exists trusted_action_requests cascade;
drop sequence if exists order_id_seq cascade;
drop sequence if exists match_id_seq cascade;
drop sequence if exists deposit_withdraw_id_seq cascade;
drop sequence if exists market_id_seq cascade;
drop sequence if exists event_log_id_seq cascade;
drop sequence if exists withdrawals_crypto_tx_id_seq cascade;
drop sequence if exists address_id_seq cascade;
drop extension pgcrypto;

COMMIT;

-- vi: expandtab ts=2 sw=2
