-- Verify txbits:INIT.schemas on pg

BEGIN;
SET ROLE txbits__owner;

SELECT '_public'::regnamespace;
ROLLBACK;

-- vi: expandtab ts=2 sw=2
