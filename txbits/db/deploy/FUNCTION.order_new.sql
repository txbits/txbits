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
      balance >= hold + new_amount * new_price;
    else
      update balances set hold = hold + new_amount
      where currency = new_base and user_id = new_user_id and
      balance >= hold + new_amount;
    end if;

    -- insufficient funds
    if not found then
      return;
    end if;

    -- trade fees
    declare
      one_way boolean;
    begin
      PERFORM trade_fees(new_base, new_counter, f2, one_way);
      if one_way then
        f := 0;
      else
        f := f2;
      end if;
    end;

    perform pg_advisory_xact_lock(-1, id) from markets where base = new_base and counter = new_counter;

    insert into orders(user_id, base, counter, original, remains, price, is_bid)
    values (new_user_id, new_base, new_counter, new_amount, new_amount, new_price, new_is_bid)
    returning * into strict o2;

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

BEGIN
  /*
   * Create test data. TODO: register factories instead
   */
  DECLARE
    c_email CONSTANT text = 'invalid email address';
    c_username CONSTANT text =  'test user name';
  BEGIN
    test_user_id = id FROM public.users WHERE email=c_email AND username=c_username;
    IF NOT FOUND THEN
      PERFORM public.create_user (
        c_email
        , 'password'
        , false -- on mailing list
        , null -- pgp
        , c_username
      );
    END IF;
  END;

  -- Give them a balance
  PERFORM public.transfer_funds(
    NULL -- from user
    , test_user_id
    , (tf.get(NULL::public.markets, 'fee override')).base
    , 100
  );
      
  -- Simple sanity check that function doesn't blow up...
  RETURN NEXT lives_ok(
    format(
      $$SELECT public.order_new(
        %L
        , NULL -- API
        , base
        , counter
        , 10
        , 30
        , false
      )
      FROM tf.get(NULL::public.markets, 'fee override')
      $$
      , test_user_id
    )
    , 'Simple test of order_new()'
  );
END
$body$
);


COMMIT;

-- vi: expandtab ts=2 sw=2
