-- Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
-- This file is licensed under the Affero General Public License version 3 or later,
-- see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

# Functions

# --- !Ups

-- when a new order is placed we try to match it
CREATE OR REPLACE FUNCTION order_insert_match() returns trigger AS $$
DECLARE
    o orders%ROWTYPE;; -- first order (chronologically)
    o2 orders%ROWTYPE;; -- second order (chronologically)
    v numeric(23,8);; -- volume of the match (when it happens)
    fee numeric(23,8);; -- fee % to be paid
BEGIN
    -- increase holds
    IF NEW.is_bid THEN
      UPDATE balances SET hold = hold + NEW.original * NEW.price WHERE currency = NEW.counter AND user_id = NEW.user_id;; --TODO: precision
    ELSE
      UPDATE balances SET hold = hold + NEW.original WHERE currency = NEW.base AND user_id = NEW.user_id;;
    END IF;;

    -- trade fees
    SELECT linear INTO STRICT fee FROM trade_fees;;

    PERFORM pg_advisory_xact_lock(id) FROM markets WHERE base = NEW.base AND counter = NEW.counter;;

    o2 := NEW;;

    IF NEW.is_bid THEN
      FOR o IN SELECT * FROM orders oo
        WHERE
          oo.remains > 0 AND
          closed = false AND
          oo.base = NEW.base AND
          oo.counter = NEW.counter AND
          NOT oo.is_bid AND
          oo.price <= NEW.price
        ORDER BY
          oo.price ASC,
          oo.created ASC
      LOOP
        -- the volume is the minimum of the two volumes
        v := LEAST(o.remains, o2.remains);;

        INSERT INTO matches (bid_user_id, ask_user_id, bid_order_id, ask_order_id, is_bid, bid_fee, ask_fee, amount, price, base, counter)
        VALUES (o2.user_id, o.user_id, o2.id, o.id, o2.is_bid, fee * v, fee * o.price * v, v, o.price, o.base, o.counter);; --TODO: precision

        -- if order was completely filled, stop matching
        SELECT * INTO STRICT o2 FROM orders WHERE id = NEW.id;;
        IF o2.remains = 0 THEN
          EXIT;;
        END IF;;
      END LOOP;;
    ELSE
      FOR o IN SELECT * FROM orders oo
        WHERE
          oo.remains > 0 AND
          closed = false AND
          oo.base = NEW.base AND
          oo.counter = NEW.counter AND
          oo.is_bid AND
          oo.price >= NEW.price
        ORDER BY
          oo.price DESC,
          oo.created ASC
      LOOP
        -- the volume is the minimum of the two volumes
        v := LEAST(o.remains, o2.remains);;

        INSERT INTO matches (bid_user_id, ask_user_id, bid_order_id, ask_order_id, is_bid, bid_fee, ask_fee, amount, price, base, counter)
        VALUES (o.user_id, o2.user_id, o.id, o2.id, o2.is_bid, fee * v, fee * o.price * v, v, o.price, o.base, o.counter);; --TODO: precision

        -- if order was completely filled, stop matching
        SELECT * INTO STRICT o2 FROM orders WHERE id = NEW.id;;
        IF o2.remains = 0 THEN
          EXIT;;
        END IF;;
      END LOOP;;
    END IF;;

    RETURN NULL;;
END;;
$$ LANGUAGE plpgsql VOLATILE COST 100;

CREATE TRIGGER order_insert_match
    AFTER INSERT ON orders
    FOR EACH ROW
    EXECUTE PROCEDURE order_insert_match();

-- cancel an order and release any holds left open
CREATE OR REPLACE FUNCTION order_cancel(o_id bigint, o_user_id bigint) returns boolean AS $$
DECLARE
  o orders%ROWTYPE;;
  b varchar(4);;
  c varchar(4);;
BEGIN
  SELECT base, counter INTO b, c FROM orders
  WHERE id = o_id AND user_id = o_user_id AND closed = false AND remains > 0;;

  IF NOT FOUND THEN
    RETURN false;;
  END IF;;

  PERFORM pg_advisory_xact_lock(id) FROM markets WHERE base = b AND counter = c;;

  UPDATE orders SET closed = true
  WHERE id = o_id AND user_id = o_user_id AND closed = false AND remains > 0
  RETURNING id, created, original, closed, remains, price, user_id, base, counter, is_bid
  INTO o;;

  IF NOT FOUND THEN
    RETURN false;;
  END IF;;

  IF o.is_bid THEN
    UPDATE balances SET hold = hold - o.remains * o.price WHERE currency = o.counter AND user_id = o.user_id;; --TODO: precision
  ELSE
    UPDATE balances SET hold = hold - o.remains WHERE currency = o.base AND user_id = o.user_id;;
  END IF;;

  RETURN true;;
END;;
$$ LANGUAGE plpgsql VOLATILE COST 100;

-- when a new match is inserted, we reduce the orders and release the holds
CREATE OR REPLACE FUNCTION match_insert() returns trigger AS $$
DECLARE
    bid orders%ROWTYPE;;
    ask orders%ROWTYPE;;
    ucount int;; -- used for debugging
BEGIN
    SELECT * INTO STRICT bid FROM orders WHERE id = NEW.bid_order_id;;
    SELECT * INTO STRICT ask FROM orders WHERE id = NEW.ask_order_id;;

    --TODO: what if they are created at the "same time"?
    IF (bid.created < ask.created AND NEW.price <> bid.price) OR (bid.created > ask.created AND NEW.price <> ask.price) THEN
      RAISE 'Attempted to match two orders but the match has the wrong price. bid: % ask: % match price: %', bid.order_id, ask.order_id, NEW.price;;
    END IF;;

    IF bid.price  < ask.price THEN
      RAISE 'Attempted to match two orders that do not agree on the price. bid: % ask: %', bid.order_id, ask.order_id;;
    END IF;;

    -- make sure the amount is the whole of one or the other order
    IF NOT (NEW.amount = bid.remains OR NEW.amount = ask.remains) THEN
      RAISE 'Match must be complete. Failed to match whole order. amount: % bid: % ask: %', NEW.amount, bid.order_id, ask.order_id;;
    END IF;;

    -- make sure the ask order is of type ask and the bid order is of type bid
    IF NOT (bid.is_bid AND NOT ask.is_bid) THEN
      RAISE 'Tried to match orders of wrong types! bid: % ask: %', NEW.amount, bid.order_id, ask.order_id;;
    END IF;;

    -- make sure the two orders are on the same market
    IF bid.base <> ask.base OR bid.counter <> ask.counter THEN
        RAISE 'Matching two orders from different markets. bid: %/% ask: %/%', bid.base, bid.counter, ask.base, ask.counter;;
    END IF;;

    -- release holds on the amount that was matched
    UPDATE balances SET hold = hold - NEW.amount WHERE currency = ask.base AND user_id = ask.user_id;;
    --TODO: precision
    UPDATE balances SET hold = hold - NEW.amount * (CASE WHEN bid.created < ask.created THEN bid.price ELSE ask.price END)
    WHERE currency = bid.counter AND user_id = bid.user_id;;

    -- Reducing order volumes and reducing remaining volumes
    UPDATE orders SET remains = remains - NEW.amount,
      closed = CASE WHEN (remains - NEW.amount) = 0 THEN true ELSE false END
      WHERE id IN (ask.id, bid.id);;

    GET DIAGNOSTICS ucount = ROW_COUNT;;

    IF ucount <> 2 THEN
      RAISE 'Expected 2 order updates, did %', ucount;;
    END IF;;

    -- insert transactions (triggers move the actual money)
    INSERT INTO transactions (from_user_id, to_user_id, currency, amount, type)
    VALUES (
        ask.user_id,
        bid.user_id,
        bid.base,
        NEW.amount,
        'M' -- match
    );;
    INSERT INTO transactions (from_user_id, to_user_id, currency, amount, type)
    VALUES (
        bid.user_id,
        ask.user_id,
        bid.counter,
        NEW.amount * (CASE WHEN bid.created < ask.created THEN bid.price ELSE ask.price END),  --TODO: precision
        'M' -- match
    );;

    -- fees transactions (triggers move the actual money)
    IF NEW.ask_fee > 0 THEN
      INSERT INTO transactions (from_user_id, to_user_id, currency, amount, type)
      VALUES (
          ask.user_id,
          0,
          ask.counter,
          NEW.ask_fee,
          'F' -- fee
      );;
    END IF;;
    IF NEW.bid_fee > 0 THEN
      INSERT INTO transactions (from_user_id, to_user_id, currency, amount, type)
      VALUES (
          bid.user_id,
          0,
          bid.base,
          NEW.bid_fee,
          'F' -- fee
      );;
    END IF;;

    RETURN NULL;;
END;;
$$ LANGUAGE plpgsql VOLATILE COST 100;

CREATE TRIGGER match_insert
    AFTER INSERT ON matches
    FOR EACH ROW
    EXECUTE PROCEDURE match_insert();

-- when a deposit is confirmed, we add money to the account
CREATE OR REPLACE FUNCTION deposit_complete() returns trigger AS $$
DECLARE
  d deposits%rowtype;;
BEGIN
    select * into d from deposits where id = NEW.id;;

    -- user 0 deposits refill hot wallets
    IF d.user_id = 0 THEN
      RETURN NULL;;
    END IF;;

    -- insert transactions (triggers move the actual money)
    INSERT INTO transactions (from_user_id, to_user_id, currency, amount, type)
      VALUES (
        null,
        d.user_id,
        d.currency,
        d.amount,
        'D' -- deposit
      );;
    -- insert transactions (triggers move the actual money)
    INSERT INTO transactions (from_user_id, to_user_id, currency, amount, type)
      VALUES (
        d.user_id,
        0,
        d.currency,
        d.fee,
        'F' -- fee
      );;
    RETURN NULL;;
END;;
$$ LANGUAGE plpgsql VOLATILE COST 100;

CREATE TRIGGER deposit_complete_crypto
    AFTER UPDATE ON deposits_crypto
    FOR EACH ROW
    WHEN (OLD.confirmed IS NULL AND NEW.confirmed IS NOT NULL)
    EXECUTE PROCEDURE deposit_complete();

CREATE TRIGGER deposit_completed_crypto
    AFTER INSERT ON deposits_crypto
    FOR EACH ROW
    WHEN (NEW.confirmed IS NOT NULL)
    EXECUTE PROCEDURE deposit_complete();


-- when a withdrawal is requested, remove the money!
CREATE OR REPLACE FUNCTION withdrawal_insert() returns trigger AS $$
DECLARE
  underflow boolean;;
  overflow boolean;;
BEGIN
  PERFORM 1 FROM balances WHERE user_id = NEW.user_id AND currency = NEW.currency FOR UPDATE;;

  SELECT limit_min > new.amount into underflow FROM withdrawal_limits where currency = NEW.currency;;

  IF underflow then
    raise 'Below lower limit for withdrawal. Tried to withdraw %.', new.amount;;
  end if;;

  SELECT ((limit_max < new.amount + (
    select coalesce(sum(amount), 0)
    from transactions
    where type = 'W' and currency = NEW.currency and from_user_id = NEW.user_id and created >= (current_timestamp - interval '24 hours' )
  )) and limit_max != -1) into overflow FROM withdrawal_limits where currency = NEW.currency;;

  IF overflow then
    raise 'Over upper limit for withdrawal. Tried to withdraw %.', new.amount;;
  end if;;

    -- insert transactions (triggers move the actual money)
    INSERT INTO transactions (from_user_id, to_user_id, currency, amount, type)
      VALUES (
        NEW.user_id,
        null,
        NEW.currency,
        NEW.amount - NEW.fee,
        'W' -- withdraw
      );;

    -- insert transactions (triggers move the actual money)
    INSERT INTO transactions (from_user_id, to_user_id, currency, amount, type)
      VALUES (
        NEW.user_id,
        0,
        NEW.currency,
        NEW.fee,
        'F' -- fee
      );;
    RETURN NULL;;
END;;
$$ LANGUAGE plpgsql VOLATILE COST 100;

CREATE TRIGGER withdrawal_insert
    AFTER INSERT ON withdrawals
    FOR EACH ROW
    EXECUTE PROCEDURE withdrawal_insert();


-- when a new transaction is created, we update the balances
CREATE OR REPLACE FUNCTION transaction_insert() returns trigger AS $$
DECLARE
  ucount int;;
BEGIN
  IF NEW.from_user_id IS NOT NULL THEN
    UPDATE balances SET balance = balance - NEW.amount WHERE user_id = NEW.from_user_id AND currency = NEW.currency;;
    GET DIAGNOSTICS ucount = ROW_COUNT;;
    IF ucount <> 1 THEN
        RAISE 'Expected 1 balance update, did %', ucount;;
    END IF;;
  END IF;;
  IF NEW.to_user_id IS NOT NULL THEN
    UPDATE balances SET balance = balance + NEW.amount WHERE user_id = NEW.to_user_id AND currency = NEW.currency;;
    GET DIAGNOSTICS ucount = ROW_COUNT;;
    IF ucount <> 1 THEN
        RAISE 'Expected 1 balance update, did %', ucount;;
    END IF;;
  END IF;;
  RETURN NULL;;
END;;
$$ LANGUAGE plpgsql VOLATILE COST 100;

CREATE TRIGGER transaction_insert
    AFTER INSERT ON transactions
    FOR EACH ROW
    EXECUTE PROCEDURE transaction_insert();

-- when updating a transaction, we update the balances
CREATE OR REPLACE FUNCTION transaction_update() returns trigger AS $$
DECLARE
  ucount int;;
BEGIN
  IF OLD.from_user_id <> NEW.from_user_id THEN
    RAISE 'Cannot change source of transaction! Old user: %, New user: %', OLD.from_user_id, NEW.from_user_id;;
  END IF;;
  IF OLD.to_user_id <> NEW.to_user_id THEN
    RAISE 'Cannot change destination of transaction! Old user: %, New user: %', OLD.to_user_id, NEW.to_user_id;;
  END IF;;
  IF OLD.currency <> NEW.currency THEN
    RAISE 'Cannot change currency of transaction! Old currency: %, New currency: %', OLD.currency, NEW.currency;;
  END IF;;

  IF OLD.from_user_id IS NOT NULL THEN
    UPDATE balances SET balance = balance + OLD.amount - NEW.amount WHERE user_id = OLD.from_user_id AND currency = OLD.currency;;
    GET DIAGNOSTICS ucount = ROW_COUNT;;
    IF ucount <> 1 THEN
        RAISE 'Expected 1 balance update, did %', ucount;;
    END IF;;
  END IF;;
  IF OLD.to_user_id IS NOT NULL THEN
    UPDATE balances SET balance = balance - OLD.amount + NEW.amount WHERE user_id = OLD.to_user_id AND currency = OLD.currency;;
    GET DIAGNOSTICS ucount = ROW_COUNT;;
    IF ucount <> 1 THEN
        RAISE 'Expected 1 balance update, did %', ucount;;
    END IF;;
  END IF;;
  RETURN NULL;;
END;;
$$ LANGUAGE plpgsql VOLATILE COST 100;

CREATE TRIGGER transaction_update
AFTER UPDATE ON transactions
FOR EACH ROW
EXECUTE PROCEDURE transaction_update();

-- create balances assicated with users
CREATE OR REPLACE FUNCTION user_insert() returns trigger AS $$
DECLARE
  ucount int;;
BEGIN
  INSERT INTO balances (user_id, currency) SELECT NEW.id, currency FROM currencies;;
  RETURN NULL;;
END;;
$$ LANGUAGE plpgsql VOLATILE COST 100;

CREATE TRIGGER user_insert
  AFTER INSERT ON users
  FOR EACH ROW
  EXECUTE PROCEDURE user_insert();

CREATE OR REPLACE FUNCTION wallets_crypto_retire() returns trigger AS $$
DECLARE
BEGIN
  UPDATE users_addresses SET assigned = current_timestamp 
  WHERE user_id = 0 AND assigned IS NULL AND currency = NEW.currency AND node_id = NEW.node_id;;

  RETURN NULL;;
END;;
$$ LANGUAGE plpgsql VOLATILE COST 100;

CREATE TRIGGER wallets_crypto_retire
  AFTER UPDATE ON wallets_crypto
  FOR EACH ROW
  WHEN (OLD.retired = false AND NEW.retired = true)
  EXECUTE PROCEDURE wallets_crypto_retire();

-- create balances associated with currencies
CREATE OR REPLACE FUNCTION currency_insert() returns trigger AS $$
DECLARE
  ucount int;;
BEGIN
  INSERT INTO balances (user_id, currency) SELECT id, NEW.currency FROM users;;
  RETURN NULL;;
END;;
$$ LANGUAGE plpgsql VOLATILE COST 100;

CREATE TRIGGER currency_insert
  AFTER INSERT ON currencies
  FOR EACH ROW
  EXECUTE PROCEDURE currency_insert();


-- https://wiki.postgresql.org/wiki/First/last_%28aggregate%29

-- Create a function that always returns the first non-NULL item
CREATE OR REPLACE FUNCTION public.first_agg ( anyelement, anyelement )
RETURNS anyelement LANGUAGE sql IMMUTABLE STRICT AS $$
        SELECT $1;;
$$;

-- And then wrap an aggregate around it
CREATE AGGREGATE public.first (
        sfunc    = public.first_agg,
        basetype = anyelement,
        stype    = anyelement
);

-- Create a function that always returns the last non-NULL item
CREATE OR REPLACE FUNCTION public.last_agg ( anyelement, anyelement )
RETURNS anyelement LANGUAGE sql IMMUTABLE STRICT AS $$
        SELECT $2;;
$$;

-- And then wrap an aggregate around it
CREATE AGGREGATE public.last (
        sfunc    = public.last_agg,
        basetype = anyelement,
        stype    = anyelement
);

# --- !Downs

DROP FUNCTION IF EXISTS order_insert_match() CASCADE;
DROP FUNCTION IF EXISTS order_cancel() CASCADE;
DROP FUNCTION IF EXISTS match_insert() CASCADE;
DROP FUNCTION IF EXISTS transaction_insert() CASCADE;
DROP FUNCTION IF EXISTS transaction_update() CASCADE;
DROP FUNCTION IF EXISTS user_insert() CASCADE;
DROP FUNCTION IF EXISTS wallets_crypto_retire() CASCADE;
DROP FUNCTION IF EXISTS currency_insert() CASCADE;
DROP FUNCTION IF EXISTS withdrawal_insert() CASCADE;
DROP FUNCTION IF EXISTS deposit_complete() CASCADE;
DROP FUNCTION IF EXISTS first_agg() CASCADE;
DROP FUNCTION IF EXISTS last_agg() CASCADE;
DROP AGGREGATE IF EXISTS first(anyelement);
DROP AGGREGATE IF EXISTS last(anyelement);
DROP TRIGGER IF EXISTS order_insert_match ON orders CASCADE;
DROP TRIGGER IF EXISTS match_insert ON matches CASCADE;
DROP TRIGGER IF EXISTS transaction_insert ON transactions CASCADE;
DROP TRIGGER IF EXISTS transaction_update ON transactions CASCADE;
DROP TRIGGER IF EXISTS user_insert ON transactions CASCADE;
DROP TRIGGER IF EXISTS wallets_crypto_retire ON transactions CASCADE;
DROP TRIGGER IF EXISTS currency_insert ON transactions CASCADE;
DROP TRIGGER IF EXISTS withdrawal_insert ON orders CASCADE;
DROP TRIGGER IF EXISTS deposit_complete_crypto ON orders CASCADE;
DROP TRIGGER IF EXISTS deposit_completed_crypto ON orders CASCADE;
DROP TRIGGER IF EXISTS deposit_complete_bank ON orders CASCADE;
