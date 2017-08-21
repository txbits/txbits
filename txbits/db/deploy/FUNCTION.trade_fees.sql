-- Deploy txbits:FUNCTION.trade_fees to pg
-- requires: TABLE.markets

BEGIN;
SET ROLE su;

-- function has default perms (everyone can execute), so safe to change ownership
ALTER FUNCTION public.trade_fees(
) OWNER TO txbits__owner; -- or, could have just dropped it...

SET ROLE txbits__owner;

-- NOTE: There's no actual change to this function in this patch; it's just here to group it with the new function
-- NOTE: that cost is silly...
create or replace function
trade_fees (
  out trade_fees
) returns setof trade_fees as $$
  select * from trade_fees;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

-- New stuff...
SET client_min_messages = WARNING; -- Squelch %TYPE noise
CREATE OR REPLACE FUNCTION public.trade_fees(
  base markets.base%TYPE
  , counter markets.counter%TYPE
  , OUT linear trade_fees.linear%TYPE
  , OUT one_way trade_fees.one_way%TYPE
) LANGUAGE plpgsql STABLE COST 1
-- WARNING
  SECURITY DEFINER SET search_path = public, pg_temp
-- WARNING
AS $body$
DECLARE
  -- Convenience constants...
  c_base CONSTANT markets.base%TYPE = base;
  c_counter CONSTANT markets.counter%TYPE = counter;

  v_fee_linear markets.fee_linear%TYPE;
  v_fee_one_way markets.fee_one_way%TYPE;
BEGIN
  -- The market should definitely exist, even if there's no override
  -- TODO: create markets__get() that does the STRICT
  SELECT INTO STRICT v_fee_linear, v_fee_one_way
      m.fee_linear , m.fee_one_way
    FROM
      markets m
    WHERE
      m.base = c_base
      AND m.counter = c_counter
  ;
  SELECT INTO STRICT linear, one_way
      coalesce(v_fee_linear, tf.linear)
      , coalesce(v_fee_one_way, tf.one_way)
    FROM trade_fees tf
  ;
  RETURN;
END
$body$;
COMMENT ON FUNCTION public.trade_fees(
  base markets.base%TYPE
  , counter markets.counter%TYPE
) IS $$Obtains fees for a specific trading pair. Uses override values if present, data from trade_fees table if not.$$;


CREATE OR REPLACE FUNCTION _test_public.__trade_fees_fn_check(
  description text
  , linear trade_fees.linear%TYPE
  , one_way trade_fees.one_way%TYPE
  , expected_linear trade_fees.linear%TYPE
  , expected_one_way trade_fees.one_way%TYPE
) RETURNS SETOF text LANGUAGE plpgsql AS $body$
BEGIN
  RETURN NEXT is(
    linear
    , expected_linear
    , 'linear should match ' || description
  );
  RETURN NEXT is(
    one_way
    , expected_one_way
    , 'one_way should match ' || description
  );
END
$body$;
  
SELECT ddl_tools.test_function(
  '_test_public.trade_fees_fn'
  , $body$
	s CONSTANT name = bs;
	f CONSTANT name = replace(fn, '_fn', '');
  f_full_name CONSTANT text = format('%I.%I', s, f);

  c_template CONSTANT text =
    $$SELECT * FROM $$ || f_full_name || $$(%L, %L)$$
  ;

  r_tf public.trade_fees;
  r_simple public.markets;
  r_override public.markets;
BEGIN
  -- Grab test data
  SELECT INTO STRICT r_tf * FROM tf.get(NULL::public.trade_fees, 'base');
  SELECT INTO STRICT r_simple * FROM tf.get(NULL::public.markets, 'base');
  SELECT INTO STRICT r_override * FROM tf.get(NULL::public.markets, 'fee override');

  RETURN NEXT throws_ok(
    format(c_template, 'bad1', 'bad2')
    , 'P0002'
    , 'query returned no rows'
    , 'selecting non-existent market throws error'
  );

  -- Sanity-check that override test data is correct
  RETURN NEXT isnt(
    r_override.fee_linear
    , r_tf.linear
    , 'sanity-check: linear SHOULD NOT match for override case'
  );
  RETURN NEXT isnt(
    r_override.fee_one_way
    , r_tf.one_way
    , 'sanity-check: one_way SHOULD NOT match for override case'
  );

  RETURN NEXT is(
    public.trade_fees()
    , r_tf
    , 'simple function should return trade fee data'
  );

  RETURN QUERY SELECT
    _test_public.__trade_fees_fn_check(
      'trade_fees table for simple case'
      , fn.linear, fn.one_way
      , r_tf.linear, r_tf.one_way
    )
    FROM public.trade_fees(r_simple.base, r_simple.counter) fn
  ;

  RETURN QUERY SELECT
    _test_public.__trade_fees_fn_check(
      'markets table for override case'
      , fn.linear, fn.one_way
      , r_override.fee_linear, r_override.fee_one_way
    )
    FROM public.trade_fees(r_override.base, r_override.counter) fn
  ;

END
$body$
);

COMMIT;

-- vi: expandtab ts=2 sw=2
