-- Revert txbits:TABLE.markets.sql from pg

BEGIN;
--SET ROLE txbits__owner;

ALTER TABLE markets
  DROP fee_linear
  , DROP fee_one_way
;

-- TODO: remove test factory registration
COMMIT;

-- vi: expandtab ts=2 sw=2
