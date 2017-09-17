-- Deploy txbits:add_market_fees to pg
-- requires: 2

BEGIN;
--SET ROLE txbits__owner;
-- when a new order is placed we try to match it
create or replace function 
order_new(
  a_uid bigint,
  a_api_key text,
  new_base varchar(4),
  new_counter varchar(4),
  new_amount numeric(23,8),
  new_price numeric(23,8),
  new_is_bid boolean,
  out new_id bigint,
  out new_remains numeric(23,8))
returns record as $$
declare
    o orders%rowtype; -- first order (chronologically)
    o2 orders%rowtype; -- second order (chronologically)
    v numeric(23,8); -- volume of the match (when it happens)
    f numeric(23,8); -- fee % for first order (maker)
    f2 numeric(23,8); -- fee % for second order (taker)
    new_user_id bigint;
begin
    if a_uid = 0 then
      raise 'User id 0 is not allowed to use this function.';
    end if;

    if a_api_key is not null then
      select user_id into new_user_id from users_api_keys
      where api_key = a_api_key and active = true and trading = true;
    else
      new_user_id := a_uid;
    end if;

    if new_user_id is null then
      return;
    end if;

    -- increase holds
    if new_is_bid then
      update balances set hold = hold + new_amount * new_price
        where currency = new_counter and user_id = new_user_id and
          balance >= hold + new_amount * new_price
      ;
      RAISE DEBUG 'currency = % AND user_id = % AND balance >= hold + %'
        , quote_literal(new_counter)
        , new_user_id
        , new_amount * new_price
      ;
    else
      update balances set hold = hold + new_amount
      where currency = new_base and user_id = new_user_id and
      balance >= hold + new_amount;
      RAISE DEBUG 'currency = % AND user_id = % AND balance >= hold + %'
        , quote_literal(new_base)
        , new_user_id
        , new_amount
      ;
    end if;

    -- insufficient funds
    if not found then
      RAISE DEBUG 'insufficient funds for user %, currency %'
        , new_user_id
        , CASE WHEN new_is_bid THEN new_counter ELSE new_base END
      ;
      return;
    end if;

    -- trade fees
    declare
      one_way boolean;
    begin
      SELECT INTO STRICT f2, one_way
          tf.linear, tf.one_way
        FROM public.trade_fees(new_base, new_counter) tf
      ;
      if one_way then
        f := 0;
      else
        f := f2;
      end if;
      RAISE DEBUG 'new_base = %, new_counter = %, f = %, f2 = %, one_way = %', new_base, new_counter, f, f2, one_way;
    end;

    perform pg_advisory_xact_lock(-1, id) from markets where base = new_base and counter = new_counter;

    insert into orders(user_id, base, counter, original, remains, price, is_bid)
    values (new_user_id, new_base, new_counter, new_amount, new_amount, new_price, new_is_bid)
    returning * into strict o2;
    RAISE DEBUG 'inserted new order %', jsonb_pretty(to_jsonb(o2));

    if new_is_bid then
      update markets set total_counter = total_counter + new_amount * new_price
      where base = new_base and counter = new_counter;

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
          oo.created asc,
          oo.id asc
      loop
        -- the volume is the minimum of the two volumes
        v := least(o.remains, o2.remains);

        RAISE DEBUG 'matching % of bid order % with order %', v, o2.id, o.id;
        perform match_new(o2.id, o.id, o2.is_bid, f2 * v, f * o.price * v, v, o.price);

        -- if order was completely filled, stop matching
        select * into strict o2 from orders where id = o2.id;
        exit when o2.remains = 0;
      end loop;
    else
      update markets set total_base = total_base + new_amount
      where base = new_base and counter = new_counter;

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
          oo.created asc,
          oo.id asc
      loop
        -- the volume is the minimum of the two volumes
        v := least(o.remains, o2.remains);

        RAISE DEBUG 'matching % of sell order % with order %', v, o2.id, o.id;
        perform match_new(o.id, o2.id, o2.is_bid, f * v, f2 * o.price * v, v, o.price);

        -- if order was completely filled, stop matching
        select * into strict o2 from orders where id = o2.id;
        exit when o2.remains = 0;
      end loop;
    end if;

    new_id := o2.id;
    new_remains := o2.remains;
    return;
end;
$$ language plpgsql volatile security definer set search_path = public, pg_temp cost 100;

SELECT ddl_tools.test_function(
  '_test_public.order_new'
  , $body$
	s CONSTANT name = bs;
	f CONSTANT name = replace(fn, '_fn', '');
  f_full_name CONSTANT text = format('%I.%I', s, f);

  test_user_id bigint;
  order_result record;

BEGIN
  /*
   * Create test data. TODO: register factories instead
   */
  DECLARE
    c_email CONSTANT text = 'invalid email address';
    c_username CONSTANT text =  'test user name';
  BEGIN
    SELECT INTO test_user_id id FROM public.users WHERE email=c_email AND username=c_username;
    IF NOT FOUND THEN
      test_user_id = public.create_user (
        c_email
        , 'password'
        , false -- on mailing list
        , null -- pgp
        , c_username
      );
    END IF;
  END;

  /*
   * Ensure there's a system user. It's not actually practical to do this one
   * via test factory...
   */
  INSERT INTO public.users(id,email)
    SELECT 0,'system'
    ON CONFLICT (id) DO NOTHING
  ;

  /*
   * Insert missing records in the balances table. This is hacky... but no easy
   * way around it via test factory.
   */
  INSERT INTO public.balances(user_id,currency)
    SELECT n.* FROM (
      SELECT id AS user_id, currency FROM public.users, tf.get(NULL::public.currencies,'base')
    ) n LEFT JOIN public.balances b USING(user_id,currency)
    WHERE b.user_id IS NULL
  ;

  /* BROKEN...
  -- Give them a balance
  PERFORM public.transfer_funds(
    NULL -- from user
    , test_user_id
    , (tf.get(NULL::public.markets, 'fee override')).base
    , 100
  );
  RAISE WARNING 'balances: %', jsonb_pretty(to_jsonb(array(SELECT row(b.*) FROM balances b)));    
  */
  UPDATE public.balances SET (balance, hold) = (100, 0)
    FROM tf.get(NULL::public.currencies,'base')
    WHERE balances.currency = get.currency
      AND user_id = test_user_id
  ;
  --RAISE WARNING 'balances: %', jsonb_pretty(to_jsonb(array(SELECT row(b.*) FROM balances b)));    

  -- Place order
  order_result := public.order_new(
        test_user_id
        , NULL -- API
        , base
        , counter
        , 2.22
        , 3.33
        , false
      )
    FROM tf.get(NULL::public.markets, 'fee override')
  ;
  RETURN NEXT ok(
    order_result IS NOT NULL -- ALL fields must not be NULL
    , 'order_new() populates all output fields'
  );

  RETURN NEXT is(
    order_result.new_remains
    , 2.22
    , 'first order not filled at all'
  );

  -- TODO: actually validate the new record in the orders table

  -- Test for insufficient funds
  order_result := public.order_new(
        test_user_id
        , NULL -- API
        , base
        , counter
        , 22222.22
        , 33333.33
        , true
      )
    FROM tf.get(NULL::public.markets, 'fee override')
  ;

  RETURN NEXT ok(
    order_result IS NULL -- True if all fields are null
    , 'order_new() returns null result with unavailable funds: ' || order_result
  );

  /* BROKEN...
  -- Give test user a balance in the other currency
  PERFORM public.transfer_funds(
    NULL -- from user
    , test_user_id
    , (tf.get(NULL::public.markets, 'fee override')).counter
    , 100
  );
  */

  order_result := public.order_new(
        test_user_id
        , NULL -- API
        , base
        , counter
        , 2.22
        , 3.33
        , true
      )
    FROM tf.get(NULL::public.markets, 'fee override')
  ;
  RETURN NEXT ok(
    order_result IS NOT NULL -- ALL fields must not be NULL
    , 'order_new() populates all output fields'
  );

  RETURN NEXT is(
    order_result.new_remains
    , 0.0
    , 'Second order should not have any remaining balance'
  );

  RETURN NEXT isnt_empty(
    format(
      $$SELECT * FROM public.matches WHERE bid_order_id = %L$$
      , order_result.new_id
    )
    , 'A match was inserted'
  );

  RETURN NEXT is(
    (SELECT bid_fee FROM public.matches WHERE bid_order_id = order_result.new_id)
    , 2.22 * (tf.get(NULL::public.markets, 'fee override')).fee_linear
    , 'match.bid_fee based on market fee'
  );

  RETURN NEXT isnt(
    (SELECT bid_fee FROM public.matches WHERE bid_order_id = order_result.new_id)
    , 2.22 * (SELECT linear FROM public.trade_fees)
    , 'match.bid_fee is NOT based on the trade_fees table'
  );

  -- TODO: test a currency pair that does not have an override
END
$body$
);


COMMIT;

-- vi: expandtab ts=2 sw=2
