-- Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
-- This file is licensed under the Affero General Public License version 3 or later,
-- see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

# Wallet

# --- !Ups

create or replace function
add_new_address (
  a_address varchar(34),
  a_currency varchar(4),
  a_node_id integer
) returns void as $$
  insert into users_addresses (address, currency, node_id)
  values (a_address, a_currency, a_node_id);;
$$ language sql volatile strict security definer set search_path = public, pg_temp cost 100;

create or replace function
free_address_count (
  a_currency varchar(4),
  a_node_id integer
) returns bigint as $$
  select count(*) from users_addresses
  where assigned is null and user_id = 0 and
  currency = a_currency and node_id = a_node_id;;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
get_min_confirmations (
  a_currency varchar(4),
  out active boolean,
  out min_deposit_confirmations integer,
  out min_withdrawal_confirmations integer
) returns record as $$
  select active, min_deposit_confirmations, min_withdrawal_confirmations
  from currencies_crypto where currency = a_currency;;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
get_node_info (
  a_currency varchar(4),
  a_node_id integer,
  out retired boolean,
  out balance_min numeric(23,8),
  out balance_warn numeric(23,8),
  out balance_target numeric(23,8),
  out balance_max numeric(23,8)
) returns record as $$
  select retired, balance_min, balance_warn, balance_target, balance_max
  from wallets_crypto where currency = a_currency and
  node_id = a_node_id;;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
get_last_block_read (
  a_currency varchar(4),
  a_node_id integer,
  out last_block_read integer,
  out last_withdrawal_time_received integer
) returns record as $$
  select last_block_read, last_withdrawal_time_received from wallets_crypto
  where currency = a_currency and node_id = a_node_id;;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
set_last_block_read (
  a_currency varchar(4),
  a_node_id integer,
  a_block_count integer,
  a_last_withdrawal_time_received integer,
  a_balance numeric(23,8)
) returns void as $$
  update wallets_crypto set last_block_read = a_block_count,
  last_withdrawal_time_received = a_last_withdrawal_time_received,
  balance = a_balance where currency = a_currency and
  node_id = a_node_id;;
$$ language sql volatile strict security definer set search_path = public, pg_temp cost 100;

create or replace function
create_deposit (
  a_currency varchar(4),
  a_node_id integer,
  a_address varchar(34),
  a_amount numeric(23,8),
  a_tx_hash varchar(64),
  a_fee numeric(23,8)
) returns bigint as $$
  with null_rows as (
    update users_addresses set assigned = current_timestamp
    where assigned is NULL and user_id = 0 and currency = a_currency and node_id = a_node_id and address = a_address
    returning user_id
  ), zero_rows as (
    insert into users_addresses (currency, node_id, address, assigned)
    select a_currency, a_node_id, a_address, current_timestamp where not exists
      (select 1 from null_rows
        union
       select 1 from users_addresses
        where assigned is not NULL and currency = a_currency and node_id = a_node_id and address = a_address
      ) returning user_id
  ), rows as (
    insert into deposits (amount, user_id, currency, fee)
      values (
        a_amount,
        (
          select user_id from null_rows
           union
          select user_id from zero_rows
           union
          select user_id from users_addresses
           where assigned is not NULL and currency = a_currency and node_id = a_node_id and address = a_address
        ),
        a_currency,
        (
          select deposit_constant + a_amount * deposit_linear from dw_fees where currency = a_currency and method = 'blockchain'
        )
      ) returning id
  )
  insert into deposits_crypto (id, amount, tx_hash, address)
    values ((select id from rows), a_amount, a_tx_hash, a_address) returning id;;
$$ language sql volatile strict security definer set search_path = public, pg_temp cost 100;

create or replace function
create_confirmed_deposit (
  a_currency varchar(4),
  a_node_id integer,
  a_address varchar(34),
  a_amount numeric(23,8),
  a_tx_hash varchar(64),
  a_fee numeric(23,8)
) returns void as $$
  with null_rows as (
    update users_addresses set assigned = current_timestamp
    where assigned is NULL and user_id = 0 and currency = a_currency and node_id = a_node_id and address = a_address
    returning user_id
  ), zero_rows as (
    insert into users_addresses (currency, node_id, address, assigned)
    select a_currency, a_node_id, a_address, current_timestamp where not exists
      (select 1 from null_rows
        union
       select 1 from users_addresses
        where assigned is not NULL and currency = a_currency and node_id = a_node_id and address = a_address
      ) returning user_id
  ), rows as (
    insert into deposits (amount, user_id, currency, fee)
      values (
        a_amount,
        (
          select user_id from null_rows
           union
          select user_id from zero_rows
           union
          select user_id from users_addresses
           where assigned is not NULL and currency = a_currency and node_id = a_node_id and address = a_address
        ),
        a_currency,
        (
          select deposit_constant + a_amount * deposit_linear from dw_fees where currency = a_currency and method = 'blockchain'
        )
      ) returning id
  )
  insert into deposits_crypto (id, amount, tx_hash, address, confirmed)
    values ((select id from rows), a_amount, a_tx_hash, a_address, current_timestamp);;
$$ language sql volatile strict security definer set search_path = public, pg_temp cost 100;

create or replace function
is_confirmed_deposit (
  a_address varchar(34),
  a_amount numeric(23,8),
  a_tx_hash varchar(64)
) returns boolean as $$
  select exists (select 1
  from deposits d inner join deposits_crypto dc on d.id = dc.id
  where dc.address = a_address and dc.tx_hash = a_tx_hash and
  d.amount = a_amount and confirmed is not NULL);;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
get_pending_deposits (
  a_currency varchar(4),
  a_node_id integer,
  out id bigint,
  out address varchar(34),
  out amount numeric(23,8),
  out tx_hash varchar(64)
) returns setof record as $$
  select d.id, dc.address, d.amount, dc.tx_hash
  from deposits d inner join deposits_crypto dc on d.id = dc.id
  inner join users_addresses a on a.address = dc.address and
  a.user_id = d.user_id and a.currency = d.currency
  where d.currency = a_currency and
  node_id = a_node_id and confirmed is NULL;;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
confirmed_deposit (
  a_id bigint,
  a_address varchar(34),
  a_tx_hash varchar(64)
) returns void as $$
  update deposits_crypto set confirmed = current_timestamp
  where id = a_id and address = a_address and
  tx_hash = a_tx_hash and confirmed is NULL;;
$$ language sql volatile strict security definer set search_path = public, pg_temp cost 100;

create or replace function
get_unconfirmed_withdrawal_tx (
  a_currency varchar(4),
  a_node_id integer,
  out id bigint,
  out tx_hash varchar(64)
) returns record as $$
  select id, tx_hash from withdrawals_crypto_tx
  where id = (select max(id) from withdrawals_crypto_tx
  where currency = a_currency and node_id = a_node_id) and
  sent is not NULL and confirmed is NULL;;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
create_withdrawal_tx (
  a_currency varchar(4),
  a_node_id integer
) returns bigint as $$
  with rows as (
  insert into withdrawals_crypto_tx (currency, node_id)
  select a_currency, a_node_id where exists (select w.id
  from withdrawals w inner join withdrawals_crypto wc on w.id = wc.id
  where currency = a_currency and withdrawals_crypto_tx_id
  is NULL) returning id
  )
  update withdrawals_crypto
  set withdrawals_crypto_tx_id = (select id from rows)
  where exists (select id from rows) and
  withdrawals_crypto_tx_id is NULL and
  id = any (select w.id
  from withdrawals w inner join withdrawals_crypto wc on w.id = wc.id
  where currency = a_currency and
  withdrawals_crypto_tx_id is NULL)
  returning withdrawals_crypto_tx_id;;
$$ language sql volatile strict security definer set search_path = public, pg_temp cost 100;

create or replace function
get_withdrawal_tx (
  a_tx_id bigint,
  out address varchar(34),
  out value numeric(23,8)
) returns setof record as $$
  select address, sum(amount - fee) as value
  from withdrawals w inner join withdrawals_crypto wc on w.id = wc.id
  where withdrawals_crypto_tx_id = a_tx_id group by address;;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
sent_withdrawal_tx (
  a_tx_id bigint,
  a_tx_hash varchar(64)
) returns void as $$
  update withdrawals_crypto_tx set sent = current_timestamp,
  tx_hash = a_tx_hash where id = a_tx_id and sent is NULL;;
$$ language sql volatile strict security definer set search_path = public, pg_temp cost 100;

create or replace function
confirmed_withdrawal_tx (
  a_tx_id bigint,
  a_tx_fee numeric(23,8)
) returns void as $$
  update withdrawals_crypto_tx set confirmed = current_timestamp,
  tx_fee = a_tx_fee where id = a_tx_id and confirmed is NULL;;
$$ language sql volatile strict security definer set search_path = public, pg_temp cost 100;

create or replace function
create_cold_storage_transfer (
  a_tx_id bigint,
  a_address varchar(34),
  a_value numeric(23,8)
) returns void as $$
  insert into withdrawals_crypto_tx_cold_storage (id, address, value)
  values (a_tx_id, a_address, a_value);;
$$ language sql volatile strict security definer set search_path = public, pg_temp cost 100;

create or replace function
set_withdrawal_tx_hash_mutated (
  a_tx_id bigint,
  a_tx_hash varchar(64)
) returns void as $$
  insert into withdrawals_crypto_tx_mutated (id, tx_hash_mutated)
  values (a_tx_id, a_tx_hash);;
$$ language sql volatile strict security definer set search_path = public, pg_temp cost 100;


# --- !Downs

drop function if exists add_new_address (varchar(34), varchar(4), integer) cascade;
drop function if exists free_address_count (varchar(4), integer) cascade;
drop function if exists get_min_confirmations (varchar(4)) cascade;
drop function if exists get_node_info (varchar(4), integer) cascade;
drop function if exists get_last_block_read (varchar(4), integer) cascade;
drop function if exists set_last_block_read (varchar(4), integer, integer, integer, numeric(23,8)) cascade;
drop function if exists create_deposit (varchar(4), integer, varchar(34), numeric(23,8), varchar(64), numeric(23,8)) cascade;
drop function if exists create_confirmed_deposit (varchar(4), integer, varchar(34), numeric(23,8), varchar(64), numeric(23,8)) cascade;
drop function if exists is_confirmed_deposit (varchar(34), varchar(64)) cascade;
drop function if exists get_pending_deposits (varchar(4), integer) cascade;
drop function if exists confirmed_deposit (bigint, varchar(34), varchar(64)) cascade;
drop function if exists get_unconfirmed_withdrawal_tx (varchar(4), integer) cascade;
drop function if exists create_withdrawal_tx (varchar(4), integer) cascade;
drop function if exists get_withdrawal_tx (bigint) cascade;
drop function if exists sent_withdrawal_tx (bigint, varchar(64)) cascade;
drop function if exists confirmed_withdrawal_tx (bigint, numeric(23,8)) cascade;
drop function if exists create_cold_storage_transfer (bigint, varchar(34), numeric(23,8)) cascade;
drop function if exists set_withdrawal_tx_hash_mutated (bigint, varchar(64)) cascade;

