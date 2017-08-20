-- Verify txbits:INIT.roles on pg

BEGIN;

-- Sufficient enough to verify... :)
SET ROLE txbits__owner;

ROLLBACK;

-- vi: expandtab ts=2 sw=2
