-- Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
-- This file is licensed under the Affero General Public License version 3 or later,
-- see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

# Functions

# --- !Ups

-- when a new order is placed we try to match it
create or replace function 
    order_new(
      new_user_id bigint,
      new_base varchar(4),
      new_counter varchar(4),
      new_amount numeric(23,8),
      new_price numeric(23,8),
      new_is_bid boolean)
    returns boolean as $$
declare
    o orders%rowtype;; -- first order (chronologically)
    o2 orders%rowtype;; -- second order (chronologically)
    v numeric(23,8);; -- volume of the match (when it happens)
    fee numeric(23,8);; -- fee % to be paid
begin
    -- increase holds
    if new_is_bid then
      update balances set hold = hold + new_amount * new_price
      where currency = new_counter and user_id = new_user_id and
      balance >= hold + new_amount * new_price;; --todo: precision
    else
      update balances set hold = hold + new_amount
      where currency = new_base and user_id = new_user_id and
      balance >= hold + new_amount;;
    end if;;

    -- insufficient funds
    if not found then
      return false;;
    end if;;

    -- trade fees
    select linear into strict fee from trade_fees;;

    perform pg_advisory_xact_lock(id) from markets where base = new_base and counter = new_counter;;

    insert into orders(user_id, base, counter, original, remains, price, is_bid)
    values (new_user_id, new_base, new_counter, new_amount, new_amount, new_price, new_is_bid)
    returning id, created, original, closed, remains, price, user_id, base, counter, is_bid
    into strict o2;;

    if new_is_bid then
      for o in select * from orders oo
        where
          oo.remains > 0 and
          oo.closed = false and
          oo.base = new_base and
          oo.counter = new_counter and
          oo.is_bid = false and
          oo.price <= new_price
        order by
          oo.price asc,
          oo.created asc
      loop
        -- the volume is the minimum of the two volumes
        v := least(o.remains, o2.remains);;

        perform match_new(o2.id, o.id, o2.is_bid, fee * v, fee * o.price * v, v, o.price);; --todo: precision

        -- if order was completely filled, stop matching
        select * into strict o2 from orders where id = o2.id;;
        if o2.remains = 0 then
          exit;;
        end if;;
      end loop;;
    else
      for o in select * from orders oo
        where
          oo.remains > 0 and
          oo.closed = false and
          oo.base = new_base and
          oo.counter = new_counter and
          oo.is_bid = true and
          oo.price >= new_price
        order by
          oo.price desc,
          oo.created asc
      loop
        -- the volume is the minimum of the two volumes
        v := least(o.remains, o2.remains);;

        perform match_new(o.id, o2.id, o2.is_bid, fee * v, fee * o.price * v, v, o.price);; --todo: precision

        -- if order was completely filled, stop matching
        select * into strict o2 from orders where id = o2.id;;
        if o2.remains = 0 then
          exit;;
        end if;;
      end loop;;
    end if;;

    return true;;
end;;
$$ language plpgsql volatile security definer cost 100;

-- cancel an order and release any holds left open
create or replace function order_cancel(o_id bigint, o_user_id bigint) returns boolean as $$
declare
    o orders%rowtype;;
    b varchar(4);;
    c varchar(4);;
begin
    select base, counter into b, c from orders
    where id = o_id and user_id = o_user_id and closed = false and remains > 0;;

    if not found then
      return false;;
    end if;;

    perform pg_advisory_xact_lock(id) from markets where base = b and counter = c;;

    update orders set closed = true
    where id = o_id and user_id = o_user_id and closed = false and remains > 0
    returning id, created, original, closed, remains, price, user_id, base, counter, is_bid
    into o;;

    if not found then
      return false;;
    end if;;

    if o.is_bid then
      update balances set hold = hold - o.remains * o.price
      where currency = o.counter and user_id = o.user_id;; --todo: precision
    else
      update balances set hold = hold - o.remains
      where currency = o.base and user_id = o.user_id;;
    end if;;

    return true;;
end;;
$$ language plpgsql volatile cost 100;

-- when a new match is inserted, we reduce the orders and release the holds
create or replace function 
    match_new(
      new_bid_order_id bigint,
      new_ask_order_id bigint,
      new_is_bid boolean,
      new_bid_fee numeric(23,8),
      new_ask_fee numeric(23,8),
      new_amount numeric(23,8),
      new_price numeric(23,8))
    returns void as $$
declare
    bid orders%rowtype;;
    ask orders%rowtype;;
    ucount int;; -- used for debugging
begin
    select * into strict bid from orders where id = new_bid_order_id;;
    select * into strict ask from orders where id = new_ask_order_id;;

    if (new_is_bid = true and new_price <> ask.price) or (new_is_bid = false and new_price <> bid.price) then
      raise 'Attempted to match two orders but the match has the wrong price. Bid: % Ask: % Price: %', bid.order_id, ask.order_id, new.price;;
    end if;;

    if bid.price < ask.price then
      raise 'Attempted to match two orders that do not agree on the price. Bid: % Ask: %', bid.order_id, ask.order_id;;
    end if;;

    -- make sure the amount is the whole of one or the other order
    if not (new_amount = bid.remains or new_amount = ask.remains) then
      raise 'Match must be complete. Failed to match whole order. Amount: % Bid: % Ask: %', new.amount, bid.order_id, ask.order_id;;
    end if;;

    -- make sure the ask order is of type ask and the bid order is of type bid
    if bid.is_bid <> true or ask.is_bid <> false then
      raise 'Tried to match orders of wrong types! Bid: % Ask: %', new.amount, bid.order_id, ask.order_id;;
    end if;;

    -- make sure the two orders are on the same market
    if bid.base <> ask.base or bid.counter <> ask.counter then
      raise 'Matching two orders from different markets. Bid: %/% Ask: %/%', bid.base, bid.counter, ask.base, ask.counter;;
    end if;;

    insert into matches (bid_user_id, ask_user_id, bid_order_id, ask_order_id, is_bid, bid_fee, ask_fee, amount, price, base, counter)
    values (bid.user_id, ask.user_id, bid.id, ask.id, new_is_bid, new_bid_fee, new_ask_fee, new_amount, new_price, bid.base, bid.counter);;

    -- release holds on the amount that was matched
    update balances set hold = hold - new_amount
    where currency = ask.base and user_id = ask.user_id;;
    --todo: precision
    update balances set hold = hold - new_amount * new_price
    where currency = bid.counter and user_id = bid.user_id;;

    -- reducing order volumes and reducing remaining volumes
    update orders set remains = remains - new_amount,
      closed = (remains - new_amount = 0)
      where id in (ask.id, bid.id);;

    get diagnostics ucount = row_count;;

    if ucount <> 2 then
      raise 'Expected 2 order updates, did %.', ucount;;
    end if;;

    perform transfer_funds(
        ask.user_id,
        bid.user_id,
        bid.base,
        new_amount
    );;
    perform transfer_funds(
        bid.user_id,
        ask.user_id,
        bid.counter,
        new_amount * new_price --todo: precision
    );;

    -- fees
    if new_ask_fee > 0 then
      perform transfer_funds(
          ask.user_id,
          0,
          ask.counter,
          new_ask_fee
      );;
    end if;;
    if new_bid_fee > 0 then
      perform transfer_funds(
          bid.user_id,
          0,
          bid.base,
          new_bid_fee
      );;
    end if;;

end;;
$$ language plpgsql volatile cost 100;

-- when a deposit is confirmed, we add money to the account
create or replace function deposit_complete() returns trigger as $$
declare
  d deposits%rowtype;;
begin
    select * into d from deposits where id = new.id;;

    -- user 0 deposits refill hot wallets
    if d.user_id = 0 then
      return null;;
    end if;;

    perform transfer_funds(
        null,
        d.user_id,
        d.currency,
        d.amount
      );;
    perform transfer_funds(
        d.user_id,
        0,
        d.currency,
        d.fee
      );;
    return null;;
end;;
$$ language plpgsql volatile cost 100;

create trigger deposit_complete_crypto
    after update on deposits_crypto
    for each row
    when (old.confirmed is null and new.confirmed is not null)
    execute procedure deposit_complete();

create trigger deposit_completed_crypto
    after insert on deposits_crypto
    for each row
    when (new.confirmed is not null)
    execute procedure deposit_complete();


-- when a withdrawal is requested, remove the money!
create or replace function withdrawal_insert() returns trigger as $$
declare
  underflow boolean;;
  overflow boolean;;
begin
  perform 1 from balances where user_id = new.user_id and currency = new.currency for update;;

  select limit_min > new.amount into underflow from withdrawal_limits where currency = new.currency;;

  if underflow then
    raise 'Below lower limit for withdrawal. Tried to withdraw %.', new.amount;;
  end if;;

  select ((limit_max < new.amount + (
    select coalesce(sum(amount), 0)
    from withdrawals
    where currency = new.currency and user_id = new.user_id and created >= (current_timestamp - interval '24 hours' )
  )) and limit_max != -1) into overflow from withdrawal_limits where currency = new.currency;;

  if overflow then
    raise 'Over upper limit for withdrawal. Tried to withdraw %.', new.amount;;
  end if;;

    perform transfer_funds(
        new.user_id,
        null,
        new.currency,
        new.amount - new.fee
      );;

    perform transfer_funds(
        new.user_id,
        0,
        new.currency,
        new.fee
      );;
    return null;;
end;;
$$ language plpgsql volatile cost 100;

create trigger withdrawal_insert
    after insert on withdrawals
    for each row
    execute procedure withdrawal_insert();


-- to transfer funds, update the balances
create or replace function
    transfer_funds(
      new_from_user_id bigint,
      new_to_user_id bigint,
      new_currency varchar(4),
      new_amount numeric(23,8))
    returns void as $$
declare
    ucount int;;
begin
    if new_from_user_id is not null then
      update balances set balance = balance - new_amount where user_id = new_from_user_id and currency = new_currency;;
      get diagnostics ucount = row_count;;
      if ucount <> 1 then
        raise 'Expected 1 balance update, did %.', ucount;;
      end if;;
    end if;;
    if new_to_user_id is not null then
      update balances set balance = balance + new_amount where user_id = new_to_user_id and currency = new_currency;;
      get diagnostics ucount = row_count;;
      if ucount <> 1 then
        raise 'Expected 1 balance update, did %.', ucount;;
      end if;;
    end if;;
end;;
$$ language plpgsql volatile cost 100;

-- create balances associated with users
create or replace function user_insert() returns trigger as $$
declare
  ucount int;;
begin
  insert into balances (user_id, currency) select new.id, currency from currencies;;
  return null;;
end;;
$$ language plpgsql volatile cost 100;

create trigger user_insert
  after insert on users
  for each row
  execute procedure user_insert();

create or replace function wallets_crypto_retire() returns trigger as $$
declare
begin
  update users_addresses set assigned = current_timestamp 
  where user_id = 0 and assigned is null and currency = new.currency and node_id = new.node_id;;

  return null;;
end;;
$$ language plpgsql volatile cost 100;

create trigger wallets_crypto_retire
  after update on wallets_crypto
  for each row
  when (old.retired = false and new.retired = true)
  execute procedure wallets_crypto_retire();

-- create balances associated with currencies
create or replace function currency_insert() returns trigger as $$
declare
  ucount int;;
begin
  insert into balances (user_id, currency) select id, new.currency from users;;
  return null;;
end;;
$$ language plpgsql volatile cost 100;

create trigger currency_insert
  after insert on currencies
  for each row
  execute procedure currency_insert();


-- https://wiki.postgresql.org/wiki/First/last_%28aggregate%29

-- create a function that always returns the first non-null item
create or replace function public.first_agg ( anyelement, anyelement )
returns anyelement language sql immutable strict as $$
        select $1;;
$$;

-- and then wrap an aggregate around it
create aggregate public.first (
        sfunc    = public.first_agg,
        basetype = anyelement,
        stype    = anyelement
);

-- create a function that always returns the last non-null item
create or replace function public.last_agg ( anyelement, anyelement )
returns anyelement language sql immutable strict as $$
        select $2;;
$$;

-- and then wrap an aggregate around it
create aggregate public.last (
        sfunc    = public.last_agg,
        basetype = anyelement,
        stype    = anyelement
);

create or replace function
create_user (
  a_password text,
  a_email varchar(256),
  a_onMailingList bool,
  out id bigint
) returns bigint as $$
  with new_user_row as (
    insert into users(email, on_mailing_list)
    values (a_email,a_onMailingList)
    returning id
  )
  insert into passwords (user_id, password) values (
    (select id from new_user_row),
    crypt(a_password, gen_salt('bf', 8))
  ) returning (select id from new_user_row);;
$$ language sql volatile cost 100;

create or replace function
update_user (
  a_id bigint,
  a_email varchar(256),
  a_onMailingList bool
) returns void as $$
  update users set email=a_email, on_mailing_list=a_onMailingList where id=a_id;;;
$$ language sql volatile cost 100;

create or replace function
user_change_password (
  a_user_id bigint,
  a_password text
) returns void as $$
  insert into passwords (user_id, password) values (a_user_id, crypt(a_password, gen_salt('bf', 8)));;
$$ language sql volatile cost 100;

create or replace function
turnon_tfa (
  a_id bigint
) returns void as $$
  update users set tfa_login=true, tfa_withdrawal=true
  where id=a_id;;
$$ language sql volatile cost 100;

create or replace function
update_tfa_secret (
  a_id bigint,
  a_secret varchar(256),
  a_typ varchar(6)
) returns void as $$
  update users set tfa_secret=a_secret, tfa_type=a_typ, tfa_login=false, tfa_withdrawal=false
  where id=a_id;;
$$ language sql volatile cost 100;

create or replace function
turnoff_tfa (
  a_id bigint
) returns void as $$
  update users set tfa_secret=NULL, tfa_login=false, tfa_withdrawal=false, tfa_type=NULL
  where id=a_id;;
$$ language sql volatile cost 100;

create or replace function
turnon_emails (
  a_id bigint
) returns void as $$
  update users set on_mailing_list=true
  where id=a_id;;
$$ language sql volatile cost 100;

create or replace function
turnoff_emails (
  a_id bigint
) returns void as $$
  update users set on_mailing_list=false
  where id=a_id;;
$$ language sql volatile cost 100;

create or replace function
add_fake_money (
  a_uid bigint,
  a_currency varchar(4),
  a_amount numeric(23,8)
) returns void as $$
  insert into deposits(amount, user_id, currency, fee) values (a_amount, a_uid, a_currency, 0);;
  select transfer_funds(null, a_uid, a_currency, a_amount);;
$$ language sql volatile cost 100;

create or replace function
remove_fake_money (
  a_uid bigint,
  a_currency varchar(4),
  a_amount numeric(23,8)
) returns void as $$
  insert into withdrawals(amount, user_id, currency, fee) values (a_amount, a_uid, a_currency, 0);;
  select transfer_funds(a_uid, null, a_currency, a_amount);;
$$ language sql volatile cost 100;

create or replace function
find_user_by_id (
  a_id bigint,
  out users
) returns setof users as $$
  select * from users
  where id = a_id;;
$$ language sql volatile cost 100;

create or replace function
find_user_by_email (
  a_email varchar(256),
  out users
) returns setof users as $$
  select * from users
  where lower(email) = lower(a_email);;
$$ language sql volatile cost 100;

create or replace function
find_user_by_email_and_password (
  a_email varchar(256),
  a_password text,
  out users
) returns setof users as $$
  with user_row as (
    select u.*, p.password from users as u
    inner join passwords as p on p.user_id = u.id
    where lower(u.email) = lower(a_email)
    order by p.created desc
    limit 1
  )
  select id, created, email, on_mailing_list, tfa_withdrawal, tfa_login, tfa_secret, tfa_type, verification, active from user_row where user_row.password = crypt(a_password, user_row.password);;
$$ language sql volatile cost 100;

create or replace function
save_token (
  a_token varchar(256),
  a_email varchar(256),
  a_is_signup bool,
  a_creation timestamp,
  a_expiration timestamp
) returns void as $$
  insert into tokens (token, email, creation, expiration, is_signup)
  values (a_token,a_email,a_creation,a_expiration,a_is_signup);;
$$ language sql volatile cost 100;

create or replace function
find_token (
  a_token varchar(256),
  out tokens
) returns setof tokens as $$
  select token, email, creation, expiration, is_signup from tokens where token = a_token;;
$$ language sql volatile cost 100;

create or replace function
delete_token (
  a_token varchar(256)
) returns void as $$
  delete from tokens where token = a_token;;
$$ language sql volatile cost 100;

create or replace function
delete_expired_tokens (
) returns void as $$
  delete from tokens where expiration < current_timestamp;;
$$ language sql volatile cost 100;

create or replace function
totp_token_is_blacklisted (
  a_token varchar(256),
  a_user bigint,
  out bool
) returns setof bool as $$
select true from totp_tokens_blacklist where user_id = a_user and token = a_token and expiration >= current_timestamp;;
$$ language sql volatile cost 100;

create or replace function
blacklist_totp_token (
  a_token varchar(256),
  a_user bigint,
  a_expiration timestamp
) returns void as $$
insert into totp_tokens_blacklist(user_id, token, expiration) values (a_user, a_token, a_expiration);;
$$ language sql volatile cost 100;

create or replace function
delete_expired_totp_blacklist_tokens (
) returns void as $$
  delete from totp_tokens_blacklist where expiration < current_timestamp;;
$$ language sql volatile cost 100;

create or replace function
new_log (
  a_user_id bigint,
  a_browser_headers text,
  a_email varchar(256),
  a_ssl_info text,
  a_browser_id text,
  a_ipv4 int,
  a_type text
) returns void as $$
  insert into event_log (user_id, email, ipv4, browser_headers, browser_id, ssl_info, type)
  values (a_user_id, a_email, a_ipv4, a_browser_headers, a_browser_id, a_ssl_info, a_type);;
$$ language sql volatile cost 100;

create or replace function
login_log (
  a_user_id bigint,
  out event_log
) returns setof event_log as $$
  select *
  from event_log
  where type in ('login_success', 'login_failure', 'logout', 'session_expired')
    and user_id = a_user_id
  order by created desc;;
$$ language sql volatile cost 100;

create or replace function
balance (
  a_uid bigint,
  out currency varchar(4),
  out amount numeric(23,8),
  out hold numeric(23,8)
) returns setof record as $$
  select c.currency, coalesce(b.balance, 0) as amount, hold from currencies c
  left outer join balances b on c.currency = b.currency and user_id = a_uid;;
$$ language sql volatile cost 100;

create or replace function
get_required_confirmations (
  out currency varchar(4),
  out min_deposit_confirmations integer
) returns setof record as $$
  select currency, min_deposit_confirmations
  from currencies_crypto where active = true;;
$$ language sql volatile cost 100;

create or replace function
get_addresses (
  a_uid bigint,
  a_currency varchar(4),
  out address varchar(34),
  out assigned timestamp
) returns setof record as $$
  with rows as (
    update users_addresses set user_id = a_uid, assigned = current_timestamp
      where assigned is NULL and user_id = 0 and currency = a_currency and
        address = (
            select address from users_addresses
            where assigned is NULL and user_id = 0 and currency = a_currency limit 1
        )
        and not exists (
            select 1 from (
                select address from users_addresses
                where user_id = a_uid and currency = a_currency order by assigned desc limit 1
            ) a
            left join deposits_crypto dc on dc.address = a.address where dc.id is NULL
        )
      returning address, assigned
  )

  (
    select address, assigned from rows
  )
  union
  (
    select address, assigned from users_addresses
    where user_id = a_uid and currency = a_currency
  )
  order by assigned desc;;
$$ language sql volatile cost 100;

create or replace function
get_all_addresses (
  a_uid bigint,
  out currency varchar(4),
  out address varchar(34),
  out assigned timestamp
) returns setof record as $$
  with rows as (
  update users_addresses set user_id = a_uid, assigned = current_timestamp
  where assigned is NULL and user_id = 0 and
  address = any (select distinct on (currency) address from users_addresses
  where assigned is NULL and user_id = 0 and currency not in (select currency
  from (select distinct on (currency) currency, address from users_addresses
  where user_id = a_uid and currency = any (select currency
  from currencies_crypto where active = true) order by currency, assigned desc)
  a left join deposits_crypto dc on dc.address = a.address where dc.id is NULL))
  returning currency, address, assigned
  )
  (select currency, address, assigned from rows)
  union
  (select currency, address, assigned from users_addresses
  where user_id = a_uid and currency = any (select currency
  from currencies_crypto where active = true))
  order by assigned desc;;
$$ language sql volatile cost 100;

create or replace function
get_all_withdrawals (
  a_uid bigint,
  out currency varchar(4),
  out amount numeric(23,8),
  out fee numeric(23,8),
  out created timestamp,
  out info text
) returns setof record as $$
  select currency, amount, fee, created, address as info
  from withdrawals_crypto wc
  inner join withdrawals w on w.id = wc.id
  where w.user_id = a_uid and withdrawals_crypto_tx_id is null
  order by created desc;;
$$ language sql volatile cost 100;

create or replace function
get_all_deposits (
  a_uid bigint,
  out currency varchar(4),
  out amount numeric(23,8),
  out fee numeric(23,8),
  out created timestamp,
  out info text
) returns setof record as $$
  select currency, amount, fee, created, address as info
  from deposits_crypto dc
  inner join deposits d on d.id = dc.id
  where d.user_id = a_uid and dc.confirmed is null
  order by created desc;;
$$ language sql volatile cost 100;

create or replace function
user_pending_trades (
  a_uid bigint,
  out id bigint,
  out is_bid bool,
  out amount numeric(23,8),
  out price numeric(23,8),
  out base varchar(4),
  out counter varchar(4),
  out created timestamp
) returns setof record as $$
  select id, is_bid, remains as amount, price, base, counter, created from orders
  where user_id = a_uid and closed = false and remains > 0
  order by created desc;;
$$ language sql volatile cost 100;

create or replace function
recent_trades (
  a_base varchar(4),
  a_counter varchar(4),
  out amount numeric(23,8),
  out created timestamp,
  out price numeric(23,8),
  out base varchar(4),
  out counter varchar(4),
  out is_bid bool
) returns setof record as $$
  select amount, created, price, base, counter, is_bid
  from matches m
  where base = a_base and counter = a_counter
  order by created desc
  limit 40;;
$$ language sql volatile cost 100 rows 40;

create or replace function
trade_history (
  a_id bigint,
  out amount numeric(23,8),
  out created timestamp,
  out price numeric(23,8),
  out base varchar(4),
  out counter varchar(4),
  out type varchar(3),
  out fee numeric(23,8)
) returns setof record as $$
  select amount, created, price, base, counter, type, fee from (
    select amount, created, price, base, counter, 'bid' as type, bid_fee as fee
    from matches where bid_user_id = a_id
    union
    select amount, created, price, base, counter, 'ask' as type, ask_fee as fee
    from matches where ask_user_id = a_id
  ) as trade_history order by created desc;;
$$ language sql volatile cost 100;

create or replace function
deposit_withdraw_history (
  a_id bigint,
  out amount numeric(23,8),
  out created timestamp,
  out currency varchar(4),
  out fee numeric(23,8),
  out type varchar(1),
  out address varchar(34)
) returns setof record as $$
  select * from
  (
    (
      select amount, created, currency, fee, 'w' as type, address
      from withdrawals w left join withdrawals_crypto wc on w.id = wc.id
      where user_id = a_id
    )
    union
    (
      select amount, created, currency, fee, 'd' as type, address
      from deposits d left join deposits_crypto dc on d.id = dc.id
      where user_id = a_id and (dc.confirmed is not null or dc.id is null)
    )
  ) as a
  order by created desc;;
$$ language sql volatile cost 100;

create or replace function
open_asks (
  a_base varchar(4),
  a_counter varchar(4),
  out amount numeric(23,8),
  out price numeric(23,8)
) returns setof record as $$
  select sum(remains) as amount, price from orders
  where not is_bid and base = a_base and counter = a_counter and closed = false and remains > 0
  group by price, base, counter order by price asc limit 40;;
$$ language sql volatile cost 100 rows 40;

create or replace function
open_bids (
  a_base varchar(4),
  a_counter varchar(4),
  out amount numeric(23,8),
  out price numeric(23,8)
) returns setof record as $$
  select sum(remains) as amount, price from orders
  where is_bid and base = a_base and counter = a_counter and closed = false and remains > 0
  group by price, base, counter order by price desc limit 40;;
$$ language sql volatile cost 100 rows 40;

create or replace function
open_orders (
  a_base varchar(4),
  a_counter varchar(4),
  out amount numeric(23,8),
  out price numeric(23,8),
  out is_bid bool
) returns setof record as $$
  (select *, true from open_bids(a_base, a_counter))
  union
  (select *, false from open_asks(a_base, a_counter));;
$$ language sql volatile cost 100 rows 40;

create or replace function
get_recent_matches (
  a_last_match timestamp,
  out amount numeric(23,8),
  out created timestamp,
  out price numeric(23,8),
  out base varchar(4),
  out counter varchar(4)
) returns setof record as $$
  select amount, m.created, m.price, m.base, m.counter
  from matches m
  where m.created > a_last_match
  order by m.created desc;;
$$ language sql volatile cost 100;

create or replace function
get_currencies (
  out currency varchar(4)
) returns setof varchar(4) as $$
  select currency from currencies order by position;;
$$ language sql volatile cost 100;

create or replace function
dw_fees (
  out dw_fees
) returns setof dw_fees as $$
  select * from dw_fees;;
$$ language sql volatile cost 100;

create or replace function
trade_fees (
  out trade_fees
) returns setof trade_fees as $$
  select * from trade_fees;;
$$ language sql volatile cost 100;

create or replace function
dw_limits (
  out withdrawal_limits
) returns setof withdrawal_limits as $$
  select * from withdrawal_limits;;
$$ language sql volatile cost 100;

create or replace function
get_pairs (
  out markets
) returns setof markets as $$
  select * from markets order by position;;
$$ language sql volatile cost 100;

create or replace function
chart_from_db (
  a_base varchar(4),
  a_counter varchar(4),
  out start_of_period timestamp,
  out volume numeric(23,8),
  out low numeric(23,8),
  out high numeric(23,8),
  out open numeric(23,8),
  out close numeric(23,8)
) returns setof record as $$
  select (date_trunc('hour', created) + INTERVAL '30 min' * ROUND(date_part('minute', created) / 30.0)) as start_of_period,
         sum(amount) as volume,
         min(price) as low,
         max(price) as high,
         first(price) as open,
         last(price) as close
  from matches
  where base = a_base and counter = a_counter and created >= (current_timestamp - interval '24 hours' )
  group by start_of_period
  order by start_of_period ASC;;
$$ language sql volatile cost 100;

create or replace function
withdraw_crypto (
  a_uid bigint,
  a_amount numeric(23,8),
  a_address varchar(34),
  a_currency varchar(4),
  out id bigint
) returns setof bigint as $$
  with rows as (
    insert into withdrawals (amount, user_id, currency, fee)
    values (a_amount, a_uid, a_currency, (select withdraw_constant + a_amount * withdraw_linear from dw_fees where currency = a_currency and method = 'blockchain')) returning id
  )
  insert into withdrawals_crypto (id, address)
  values ((select id from rows), a_address) returning id;;
$$ language sql volatile cost 100;


# --- !Downs

drop function if exists order_new(bigint, varchar(4), varchar(4), numeric(23,8), numeric(23,8), boolean) cascade;
drop function if exists order_cancel(bigint, bigint) cascade;
drop function if exists match_new(bigint, bigint, boolean, numeric(23,8), numeric(23,8), numeric(23,8), numeric(23,8)) cascade;
drop function if exists transfer_funds(bigint, bigint, varchar(4), numeric(23,8)) cascade;
drop function if exists user_insert() cascade;
drop function if exists wallets_crypto_retire() cascade;
drop function if exists currency_insert() cascade;
drop function if exists withdrawal_insert() cascade;
drop function if exists deposit_complete() cascade;
drop function if exists first_agg() cascade;
drop function if exists last_agg() cascade;
drop aggregate if exists first(anyelement);
drop aggregate if exists last(anyelement);

drop function if exists  create_user (text, varchar(256), bool) cascade;
drop function if exists  update_user (bigint, varchar(256), bool) cascade;
drop function if exists  user_change_password (bigint, text) cascade;
drop function if exists  turnon_tfa (bigint) cascade;
drop function if exists  update_tfa_secret (bigint, varchar(256), varchar(6)) cascade;
drop function if exists  turnoff_tfa (bigint) cascade;
drop function if exists  turnon_emails (bigint) cascade;
drop function if exists  turnoff_emails (bigint) cascade;
drop function if exists  add_fake_money (bigint, varchar(4), numeric(23,8)) cascade;
drop function if exists  remove_fake_money (bigint, varchar(4), numeric(23,8)) cascade;
drop function if exists  find_user_by_id (bigint) cascade;
drop function if exists  find_user_by_email (varchar(256)) cascade;
drop function if exists  find_user_by_email_and_password (varchar(256), text) cascade;
drop function if exists  save_token (varchar(256), varchar(256), bool, timestamp, timestamp) cascade;
drop function if exists  find_token (varchar(256)) cascade;
drop function if exists  delete_token (varchar(256)) cascade;
drop function if exists  delete_expired_tokens () cascade;
drop function if exists  totp_token_is_blacklisted (varchar(256), bigint) cascade;
drop function if exists  blacklist_totp_token (varchar(256), bigint, timestamp) cascade;
drop function if exists  delete_expired_totp_blacklist_tokens () cascade;
drop function if exists  new_log (bigint, text, varchar(256), text, text, int, text) cascade;
drop function if exists  login_log (bigint) cascade;
drop function if exists  balance (bigint) cascade;
drop function if exists  get_required_confirmations () cascade;
drop function if exists  get_addresses (bigint, varchar(4)) cascade;
drop function if exists  get_all_addresses (bigint) cascade;
drop function if exists  get_all_withdrawals (bigint) cascade;
drop function if exists  get_all_deposits (bigint) cascade;
drop function if exists  user_pending_trades (bigint) cascade;
drop function if exists  recent_trades (varchar(4), varchar(4)) cascade;
drop function if exists  trade_history (bigint) cascade;
drop function if exists  deposit_withdraw_history (bigint) cascade;
drop function if exists  open_asks (varchar(4), varchar(4)) cascade;
drop function if exists  open_bids (varchar(4), varchar(4)) cascade;
drop function if exists  get_recent_matches (timestamp) cascade;
drop function if exists  get_currencies () cascade;
drop function if exists  dw_fees () cascade;
drop function if exists  trade_fees () cascade;
drop function if exists  dw_limits () cascade;
drop function if exists  get_pairs () cascade;
drop function if exists  chart_from_db (varchar(4), varchar(4)) cascade;
drop function if exists  withdraw_crypto (bigint, numeric(23,8), varchar(34), varchar(4)) cascade;

drop trigger if exists user_insert on users cascade;
drop trigger if exists wallets_crypto_retire on wallets_crypto cascade;
drop trigger if exists currency_insert on currencies cascade;
drop trigger if exists withdrawal_insert on withdrawals cascade;
drop trigger if exists deposit_complete_crypto on deposits_crypto cascade;
drop trigger if exists deposit_completed_crypto on deposits_crypto cascade;

