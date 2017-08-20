-- Deploy txbits:TABLE.markets.sql to pg
-- requires: 2

BEGIN;
--SET ROLE txbits__owner;

-- Allow for per-market fee configuration
ALTER TABLE markets
  ADD fee_linear numeric(23,8) CONSTRAINT markets_fee_linear_check CHECK(fee_linear IS NULL OR fee_linear >= 0)
  , ADD fee_one_way boolean
;
COMMIT;

-- vi: expandtab ts=2 sw=2
