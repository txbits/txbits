-- Revert txbits:INIT.pgtap from pg

BEGIN;
SET ROLE su;

DROP SCHEMA tap CASCADE;
COMMIT;

-- vi: expandtab ts=2 sw=2
