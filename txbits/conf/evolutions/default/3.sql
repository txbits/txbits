-- TxBits - An open source Bitcoin and crypto currency exchange
-- Copyright (C) 2014-2015  Viktor Stanchev & Kirk Zathey
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public License
-- along with this program.  If not, see <http://www.gnu.org/licenses/>.

# Wallet

# --- !Ups

create or replace function
free_address_count (
  a_currency varchar(4),
  a_node_id integer
) returns bigint as $$
  select count(*) from users_addresses
  where assigned is null and user_id = 0 and
  currency = a_currency and node_id = a_node_id;;
$$ language sql stable security invoker set search_path = public, pg_temp cost 100;

create or replace function
get_min_confirmations (
  a_currency varchar(4),
  out active boolean,
  out min_deposit_confirmations integer,
  out min_withdrawal_confirmations integer
) returns record as $$
  select active, min_deposit_confirmations, min_withdrawal_confirmations
  from currencies_crypto where currency = a_currency;;
$$ language sql stable security invoker set search_path = public, pg_temp cost 100;

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
$$ language sql stable security invoker set search_path = public, pg_temp cost 100;

create or replace function
get_balance (
  a_currency varchar(4),
  a_node_id integer
) returns numeric(23,8) as $$
  select balance from wallets_crypto
  where currency = a_currency and node_id = a_node_id;;
$$ language sql stable security invoker set search_path = public, pg_temp cost 100;

create or replace function
get_last_block_read (
  a_currency varchar(4),
  a_node_id integer,
  out last_block_read integer,
  out last_withdrawal_time_received integer
) returns record as $$
  select last_block_read, last_withdrawal_time_received from wallets_crypto
  where currency = a_currency and node_id = a_node_id;;
$$ language sql stable security invoker set search_path = public, pg_temp cost 100;

create or replace function
set_last_block_read (
  a_currency varchar(4),
  a_node_id integer,
  a_block_count integer,
  a_last_withdrawal_time_received integer
) returns void as $$
  update wallets_crypto set last_block_read = a_block_count,
  last_withdrawal_time_received = a_last_withdrawal_time_received
  where currency = a_currency and
  node_id = a_node_id;;
$$ language sql volatile security invoker set search_path = public, pg_temp cost 100;

create or replace function
create_deposit (
  a_currency varchar(4),
  a_node_id integer,
  a_address varchar(35),
  a_amount numeric(23,8),
  a_tx_hash varchar(64)
) returns bigint as $$
declare
  deposit_uid bigint;;
  deposit_id bigint;;
begin
  select user_id into deposit_uid from users_addresses
    where currency = a_currency and node_id = a_node_id and address = a_address;;
  
  if deposit_uid is null then
    insert into users_addresses (currency, node_id, address, assigned)
      values (a_currency, a_node_id, a_address, current_timestamp);;
    deposit_uid := 0;;
  elsif deposit_uid = 0 then
    update users_addresses set assigned = current_timestamp
      where assigned is NULL and user_id = 0 and currency = a_currency and node_id = a_node_id and address = a_address;;
  end if;;

  insert into deposits (amount, user_id, currency, fee)
    values (a_amount, deposit_uid, a_currency,
      (select deposit_constant + a_amount * deposit_linear from dw_fees where currency = a_currency and method = 'blockchain')
    ) returning id into strict deposit_id;;

  insert into deposits_crypto (id, amount, tx_hash, address)
    values (deposit_id, a_amount, a_tx_hash, a_address);;

  return deposit_id;;
end;;
$$ language plpgsql volatile security invoker set search_path = public, pg_temp cost 100;

create or replace function
create_confirmed_deposit (
  a_currency varchar(4),
  a_node_id integer,
  a_address varchar(35),
  a_amount numeric(23,8),
  a_tx_hash varchar(64)
) returns void as $$
declare
  deposit_id bigint;;
begin
  select create_deposit(a_currency, a_node_id, a_address, a_amount, a_tx_hash) into strict deposit_id;;

  perform confirmed_deposit(deposit_id, a_address, a_tx_hash, a_node_id);;
end;;
$$ language plpgsql volatile security invoker set search_path = public, pg_temp cost 100;

create or replace function
is_confirmed_deposit (
  a_address varchar(35),
  a_amount numeric(23,8),
  a_tx_hash varchar(64)
) returns boolean as $$
  select exists (select 1
  from deposits d inner join deposits_crypto dc on d.id = dc.id
  where dc.address = a_address and dc.tx_hash = a_tx_hash and
  d.amount = a_amount and confirmed is not NULL);;
$$ language sql stable security invoker set search_path = public, pg_temp cost 100;

create or replace function
get_pending_deposits (
  a_currency varchar(4),
  a_node_id integer,
  out id bigint,
  out address varchar(35),
  out amount numeric(23,8),
  out tx_hash varchar(64)
) returns setof record as $$
  select d.id, dc.address, d.amount, dc.tx_hash
  from deposits d inner join deposits_crypto dc on d.id = dc.id
  inner join users_addresses a on a.address = dc.address and
  a.user_id = d.user_id and a.currency = d.currency
  where d.currency = a_currency and
  node_id = a_node_id and confirmed is NULL;;
$$ language sql stable security invoker set search_path = public, pg_temp cost 100;

create or replace function
confirmed_deposit (
  a_id bigint,
  a_address varchar(35),
  a_tx_hash varchar(64),
  a_node_id integer
) returns void as $$
declare
  d deposits%rowtype;;
begin
  select * into strict d from deposits where id = a_id;;

  update deposits_crypto set confirmed = current_timestamp
  where id = a_id and address = a_address and
  tx_hash = a_tx_hash and confirmed is NULL;;

  if found then
    update wallets_crypto set balance = balance + d.amount
    where currency = d.currency and node_id = a_node_id;;

    -- user 0 deposits refill hot wallets
    if d.user_id <> 0 then
      -- when a deposit is confirmed, we add money to the account
      perform transfer_funds(
        null,
        d.user_id,
        d.currency,
        d.amount
      );;
      if d.fee > 0 then
        perform transfer_funds(
          d.user_id,
          0,
          d.currency,
          d.fee
        );;
      end if;;
    end if;;
  end if;;
end;;
$$ language plpgsql volatile security invoker set search_path = public, pg_temp cost 100;

create or replace function
get_unconfirmed_withdrawal_tx (
  a_currency varchar(4),
  a_node_id integer,
  out id bigint,
  out tx_hash varchar(64)
) returns record as $$
  select id, tx_hash from withdrawals_crypto_tx
    where id = (select max(id) from withdrawals_crypto_tx
      where currency = a_currency and node_id = a_node_id)
    and confirmed is NULL;;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
get_last_confirmed_withdrawal_tx (
  a_currency varchar(4),
  a_node_id integer,
  out confirmed_id bigint,
  out confirmed_tx_hash varchar(64)
) returns record as $$
declare
begin
  select max(id) into confirmed_id from withdrawals_crypto_tx
    where currency = a_currency and node_id = a_node_id and
    sent is not NULL and confirmed is not NULL;;

  select coalesce(tx_hash_mutated, tx_hash) into confirmed_tx_hash from
    withdrawals_crypto_tx wct left join withdrawals_crypto_tx_mutated wctm
    on wct.id = wctm.id where wct.id = confirmed_id;;
end;;
$$ language plpgsql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
create_withdrawal_tx (
  a_currency varchar(4),
  a_node_id integer
) returns bigint as $$
declare
  w_id bigint;;
begin
  insert into withdrawals_crypto_tx (currency, node_id)
    values (a_currency, a_node_id) returning id into strict w_id;;

  -- Enforce a low limit of 300 withdrawals per batch
  -- Size limit of a standard Bitcoin transaction is 100 KB
  -- Size in bytes = inputs * 180 + outputs * 34 + 10 +/- inputs
  update withdrawals_crypto ww set withdrawals_crypto_tx_id = w_id
    from (
      select w.id
      from withdrawals w inner join withdrawals_crypto wc on w.id = wc.id
      where currency = a_currency and w.user_confirmed = true and withdrawals_crypto_tx_id is NULL
      limit 300
    ) w2 where ww.id = w2.id;;

  if not found then
    delete from withdrawals_crypto_tx where id = w_id;;
    return null;;
  else
    return w_id;;
  end if;;
end;;
$$ language plpgsql volatile security invoker set search_path = public, pg_temp cost 100;

create or replace function
get_withdrawal_tx_data (
  a_tx_id bigint,
  out address varchar(35),
  out value numeric(23,8)
) returns setof record as $$
  select address, sum(amount - fee) as value
  from withdrawals w inner join withdrawals_crypto wc on w.id = wc.id
  where withdrawals_crypto_tx_id = a_tx_id group by address;;
$$ language sql stable security invoker set search_path = public, pg_temp cost 100;

create or replace function
sent_withdrawal_tx (
  a_tx_id bigint,
  a_tx_hash varchar(64),
  a_tx_amount numeric(23,8)
) returns void as $$
declare
  wct withdrawals_crypto_tx%rowtype;;
begin
  update withdrawals_crypto_tx set sent = current_timestamp,
    tx_hash = a_tx_hash, tx_amount = a_tx_amount
    where id = a_tx_id and sent is NULL
    returning * into strict wct;;

  update wallets_crypto set balance = balance - wct.tx_amount
    where currency = wct.currency and node_id = wct.node_id;;
end;;
$$ language plpgsql volatile security invoker set search_path = public, pg_temp cost 100;

create or replace function
confirmed_withdrawal_tx (
  a_tx_id bigint,
  a_tx_fee numeric(23,8)
) returns void as $$
declare
  wct withdrawals_crypto_tx%rowtype;;
begin
  update withdrawals_crypto_tx set confirmed = current_timestamp,
    tx_fee = a_tx_fee where id = a_tx_id and confirmed is NULL
    returning * into strict wct;;

  update wallets_crypto set balance = balance - wct.tx_fee
    where currency = wct.currency and node_id = wct.node_id;;
end;;
$$ language plpgsql volatile security invoker set search_path = public, pg_temp cost 100;

create or replace function
create_cold_storage_transfer (
  a_tx_id bigint,
  a_address varchar(35),
  a_value numeric(23,8)
) returns void as $$
  insert into withdrawals_crypto_tx_cold_storage (id, address, value)
  values (a_tx_id, a_address, a_value);;
$$ language sql volatile security invoker set search_path = public, pg_temp cost 100;

create or replace function
get_cold_storage_transfer (
  a_tx_id bigint,
  out address varchar(35),
  out value numeric(23,8)
) returns record as $$
  select address, value from withdrawals_crypto_tx_cold_storage
  where id = a_tx_id;;
$$ language sql stable security invoker set search_path = public, pg_temp cost 100;

create or replace function
set_withdrawal_tx_hash_mutated (
  a_tx_id bigint,
  a_tx_hash varchar(64)
) returns void as $$
  insert into withdrawals_crypto_tx_mutated (id, tx_hash_mutated)
  values (a_tx_id, a_tx_hash);;
$$ language sql volatile security invoker set search_path = public, pg_temp cost 100;


# --- !Downs

drop function if exists free_address_count (varchar(4), integer) cascade;
drop function if exists get_min_confirmations (varchar(4)) cascade;
drop function if exists get_node_info (varchar(4), integer) cascade;
drop function if exists get_balance (varchar(4), integer) cascade;
drop function if exists get_last_block_read (varchar(4), integer) cascade;
drop function if exists set_last_block_read (varchar(4), integer, integer, integer) cascade;
drop function if exists create_deposit (varchar(4), integer, varchar(35), numeric(23,8), varchar(64)) cascade;
drop function if exists create_confirmed_deposit (varchar(4), integer, varchar(35), numeric(23,8), varchar(64)) cascade;
drop function if exists is_confirmed_deposit (varchar(35), varchar(64)) cascade;
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

