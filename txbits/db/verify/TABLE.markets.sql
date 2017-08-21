-- Verify txbits:TABLE.markets.sql on pg

BEGIN;
--SET ROLE txbits__owner;

-- Select both just to make sure they don't conflict...
SELECT 1/count(*) FROM tf.get(NULL::markets, 'base');
SELECT 1/count(*) FROM tf.get(NULL::markets, 'fee override');
ROLLBACK;

-- vi: expandtab ts=2 sw=2
