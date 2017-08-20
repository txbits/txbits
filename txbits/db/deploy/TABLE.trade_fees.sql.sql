-- Deploy txbits:TABLE.trade_fees.sql to pg
-- requires: 2

BEGIN;
--SET ROLE txbits__owner;

ALTER TABLE trade_fees
  ADD one_row_only boolean NOT NULL DEFAULT true
    CONSTRAINT trade_fees__pk_one_row_only PRIMARY KEY
    CONSTRAINT trade_fees_may_only_have_on_row CHECK(one_row_only)
;
COMMIT;

-- vi: expandtab ts=2 sw=2
