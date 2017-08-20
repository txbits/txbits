-- Revert txbits:TABLE.trade_fees.sql from pg

BEGIN;
--SET ROLE txbits__owner;

ALTER TABLE trade_fees DROP one_row_only;
COMMIT;

-- vi: expandtab ts=2 sw=2
