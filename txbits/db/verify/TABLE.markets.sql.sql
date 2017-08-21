-- Verify txbits:TABLE.markets.sql on pg

BEGIN;
--SET ROLE txbits__owner;

SELECT 1/count(*) FROM tf.get(NULL::markets, 'base');
ROLLBACK;

-- vi: expandtab ts=2 sw=2
