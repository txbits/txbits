-- Verify txbits:TABLE.currencies on pg

BEGIN;
SET ROLE txbits__owner;

SELECT 1/count(*) FROM tf.get(NULL::currencies,'base');
ROLLBACK;

-- vi: expandtab ts=2 sw=2
