-- Verify txbits:INIT.pgtap on pg

BEGIN;
SET ROLE su;

SELECT 'tap.ok(boolean)'::regprocedure;

ROLLBACK;

-- vi: expandtab ts=2 sw=2
