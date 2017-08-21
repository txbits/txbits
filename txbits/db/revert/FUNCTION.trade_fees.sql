-- Revert txbits:FUNCTION.trade_fees from pg

BEGIN;
SET ROLE txbits__owner;

DROP FUNCTION _test_public.trade_fees_fn();
SET client_min_messages = WARNING; -- Squelch %TYPE noise
DROP FUNCTION _test_public.__trade_fees_fn_check(
  description text
  , linear trade_fees.linear%TYPE
  , one_way trade_fees.one_way%TYPE
  , expected_linear trade_fees.linear%TYPE
  , expected_one_way trade_fees.one_way%TYPE
);

DROP FUNCTION public.trade_fees(
  base markets.base%TYPE
  , counter markets.counter%TYPE
);

-- DROP of simple function intentionally omitted!
COMMIT;

-- vi: expandtab ts=2 sw=2
