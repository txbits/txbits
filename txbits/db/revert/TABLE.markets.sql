-- Revert txbits:TABLE.markets.sql from pg

BEGIN;
--SET ROLE txbits__owner;

DROP FUNCTION _test_public.markets();

-- TODO: remove test factory registration

ALTER TABLE markets
  DROP fee_linear
  , DROP fee_one_way
;

COMMIT;

-- vi: expandtab ts=2 sw=2
