-- Verify txbits:TABLE.trade_fees.sql on pg

BEGIN;
--SET ROLE txbits__owner;

SELECT one_row_only FROM trade_fees WHERE false;
ROLLBACK;

-- vi: expandtab ts=2 sw=2
