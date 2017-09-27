-- Revert txbits:TABLE.trade_fees.sql from pg

BEGIN;
--SET ROLE txbits__owner;

DROP FUNCTION _test_public.trade_fees();
DROP FUNCTION  _test_public.__trade_fees__set(
  linear public.trade_fees.linear%TYPE
  , one_way public.trade_fees.one_way%TYPE
);
ALTER TABLE trade_fees DROP one_row_only;
COMMIT;

-- vi: expandtab ts=2 sw=2
