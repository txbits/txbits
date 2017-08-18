-- Revert txbits:3 from pg

BEGIN;
--SET ROLE txbits__owner;

drop function if exists free_address_count (varchar(4), integer) cascade;
drop function if exists get_min_confirmations (varchar(4)) cascade;
drop function if exists get_node_info (varchar(4), integer) cascade;
drop function if exists get_balance (varchar(4), integer) cascade;
drop function if exists get_last_block_read (varchar(4), integer) cascade;
drop function if exists set_last_block_read (varchar(4), integer, integer, integer) cascade;
drop function if exists create_deposit (varchar(4), integer, varchar(35), numeric(23,8), varchar(64)) cascade;
drop function if exists create_confirmed_deposit (varchar(4), integer, varchar(35), numeric(23,8), varchar(64)) cascade;
drop function if exists is_confirmed_deposit (
  a_address varchar(35),
  a_amount numeric(23,8),
  a_tx_hash varchar(64)
);
drop function if exists get_pending_deposits (varchar(4), integer) cascade;
drop function if exists confirmed_deposit (bigint, varchar(35), varchar(64), integer) cascade;
drop function if exists get_unconfirmed_withdrawal_tx (varchar(4), integer) cascade;
drop function if exists get_last_confirmed_withdrawal_tx (varchar(4), integer) cascade;
drop function if exists create_withdrawal_tx (varchar(4), integer) cascade;
drop function if exists get_withdrawal_tx_data (bigint) cascade;
drop function if exists sent_withdrawal_tx (bigint, varchar(64), numeric(23,8)) cascade;
drop function if exists confirmed_withdrawal_tx (bigint, numeric(23,8)) cascade;
drop function if exists create_cold_storage_transfer (bigint, varchar(35), numeric(23,8)) cascade;
drop function if exists get_cold_storage_transfer (bigint) cascade;
drop function if exists set_withdrawal_tx_hash_mutated (bigint, varchar(64)) cascade;

COMMIT;

-- vi: expandtab ts=2 sw=2
