-- Verify txbits:TABLE.markets.sql on pg

BEGIN;
--SET ROLE txbits__owner;

SELECT fee_linear, fee_one_way FROM markets WHERE false;
ROLLBACK;

-- vi: expandtab ts=2 sw=2
