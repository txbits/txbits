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
      raise 'attempted to match two orders but the match has the wrong price. bid: % ask: % match price: %', bid.order_id, ask.order_id, new.price;;
    end if;;

    if bid.price < ask.price then
      raise 'attempted to match two orders that do not agree on the price. bid: % ask: %', bid.order_id, ask.order_id;;
    end if;;

    -- make sure the amount is the whole of one or the other order
    if not (new_amount = bid.remains or new_amount = ask.remains) then
      raise 'match must be complete. failed to match whole order. amount: % bid: % ask: %', new.amount, bid.order_id, ask.order_id;;
    end if;;

    -- make sure the ask order is of type ask and the bid order is of type bid
    if bid.is_bid <> true or ask.is_bid <> false then
      raise 'tried to match orders of wrong types! bid: % ask: %', new.amount, bid.order_id, ask.order_id;;
    end if;;

    -- make sure the two orders are on the same market
    if bid.base <> ask.base or bid.counter <> ask.counter then
      raise 'matching two orders from different markets. bid: %/% ask: %/%', bid.base, bid.counter, ask.base, ask.counter;;
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
      raise 'expected 2 order updates, did %', ucount;;
    end if;;

    -- insert transactions (triggers move the actual money)
    insert into transactions (from_user_id, to_user_id, currency, amount, type)
    values (
        ask.user_id,
        bid.user_id,
        bid.base,
        new_amount,
        'M' -- match
    );;
    insert into transactions (from_user_id, to_user_id, currency, amount, type)
    values (
        bid.user_id,
        ask.user_id,
        bid.counter,
        new_amount * new_price, --todo: precision
        'M' -- match
    );;

    -- fees transactions (triggers move the actual money)
    if new_ask_fee > 0 then
      insert into transactions (from_user_id, to_user_id, currency, amount, type)
      values (
          ask.user_id,
          0,
          ask.counter,
          new_ask_fee,
          'F' -- fee
      );;
    end if;;
    if new_bid_fee > 0 then
      insert into transactions (from_user_id, to_user_id, currency, amount, type)
      values (
          bid.user_id,
          0,
          bid.base,
          new_bid_fee,
          'F' -- fee
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

    -- insert transactions (triggers move the actual money)
    insert into transactions (from_user_id, to_user_id, currency, amount, type)
      values (
        null,
        d.user_id,
        d.currency,
        d.amount,
        'D' -- deposit
      );;
    -- insert transactions (triggers move the actual money)
    insert into transactions (from_user_id, to_user_id, currency, amount, type)
      values (
        d.user_id,
        0,
        d.currency,
        d.fee,
        'F' -- fee
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
    raise 'Below lower limit for withdrawal. tried to withdraw %.', new.amount;;
  end if;;

  select ((limit_max < new.amount + (
    select coalesce(sum(amount), 0)
    from transactions
    where type = 'W' and currency = new.currency and from_user_id = new.user_id and created >= (current_timestamp - interval '24 hours' )
  )) and limit_max != -1) into overflow from withdrawal_limits where currency = new.currency;;

  if overflow then
    raise 'Over upper limit for withdrawal. tried to withdraw %.', new.amount;;
  end if;;

    -- insert transactions (triggers move the actual money)
    insert into transactions (from_user_id, to_user_id, currency, amount, type)
      values (
        new.user_id,
        null,
        new.currency,
        new.amount - new.fee,
        'W' -- withdraw
      );;

    -- insert transactions (triggers move the actual money)
    insert into transactions (from_user_id, to_user_id, currency, amount, type)
      values (
        new.user_id,
        0,
        new.currency,
        new.fee,
        'F' -- fee
      );;
    return null;;
end;;
$$ language plpgsql volatile cost 100;

create trigger withdrawal_insert
    after insert on withdrawals
    for each row
    execute procedure withdrawal_insert();


-- when a new transaction is created, we update the balances
create or replace function transaction_insert() returns trigger as $$
declare
  ucount int;;
begin
  if new.from_user_id is not null then
    update balances set balance = balance - new.amount where user_id = new.from_user_id and currency = new.currency;;
    get diagnostics ucount = row_count;;
    if ucount <> 1 then
        raise 'Expected 1 balance update, did %', ucount;;
    end if;;
  end if;;
  if new.to_user_id is not null then
    update balances set balance = balance + new.amount where user_id = new.to_user_id and currency = new.currency;;
    get diagnostics ucount = row_count;;
    if ucount <> 1 then
        raise 'Expected 1 balance update, did %', ucount;;
    end if;;
  end if;;
  return null;;
end;;
$$ language plpgsql volatile cost 100;

create trigger transaction_insert
    after insert on transactions
    for each row
    execute procedure transaction_insert();

-- when updating a transaction, we update the balances
create or replace function transaction_update() returns trigger as $$
declare
  ucount int;;
begin
  if old.from_user_id <> new.from_user_id then
    raise 'Cannot change source of transaction! old user: %, new user: %', old.from_user_id, new.from_user_id;;
  end if;;
  if old.to_user_id <> new.to_user_id then
    raise 'Cannot change destination of transaction! old user: %, new user: %', old.to_user_id, new.to_user_id;;
  end if;;
  if old.currency <> new.currency then
    raise 'Cannot change currency of transaction! old currency: %, new currency: %', old.currency, new.currency;;
  end if;;

  if old.from_user_id is not null then
    update balances set balance = balance + old.amount - new.amount where user_id = old.from_user_id and currency = old.currency;;
    get diagnostics ucount = row_count;;
    if ucount <> 1 then
        raise 'Expected 1 balance update, did %', ucount;;
    end if;;
  end if;;
  if old.to_user_id is not null then
    update balances set balance = balance - old.amount + new.amount where user_id = old.to_user_id and currency = old.currency;;
    get diagnostics ucount = row_count;;
    if ucount <> 1 then
        raise 'Expected 1 balance update, did %', ucount;;
    end if;;
  end if;;
  return null;;
end;;
$$ language plpgsql volatile cost 100;

create trigger transaction_update
after update on transactions
for each row
execute procedure transaction_update();

-- create balances assicated with users
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

# --- !Downs

drop function if exists order_new(bigint, varchar(4), varchar(4), numeric(23,8), numeric(23,8), boolean) cascade;
drop function if exists order_cancel(bigint, bigint) cascade;
drop function if exists match_new(bigint, bigint, boolean, numeric(23,8), numeric(23,8), numeric(23,8), numeric(23,8)) cascade;
drop function if exists transaction_insert() cascade;
drop function if exists transaction_update() cascade;
drop function if exists user_insert() cascade;
drop function if exists wallets_crypto_retire() cascade;
drop function if exists currency_insert() cascade;
drop function if exists withdrawal_insert() cascade;
drop function if exists deposit_complete() cascade;
drop function if exists first_agg() cascade;
drop function if exists last_agg() cascade;
drop aggregate if exists first(anyelement);
drop aggregate if exists last(anyelement);
drop trigger if exists transaction_insert on transactions cascade;
drop trigger if exists transaction_update on transactions cascade;
drop trigger if exists user_insert on transactions cascade;
drop trigger if exists wallets_crypto_retire on transactions cascade;
drop trigger if exists currency_insert on transactions cascade;
drop trigger if exists withdrawal_insert on orders cascade;
drop trigger if exists deposit_complete_crypto on orders cascade;
drop trigger if exists deposit_completed_crypto on orders cascade;
