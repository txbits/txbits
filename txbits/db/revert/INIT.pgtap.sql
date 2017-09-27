-- Revert txbits:INIT.pgtap from pg

BEGIN;
SET ROLE su;

DROP EXTENSION pgtap;
DROP SCHEMA tap;
COMMIT;

-- vi: expandtab ts=2 sw=2
