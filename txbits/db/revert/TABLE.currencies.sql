-- Revert txbits:TABLE.currencies from pg

BEGIN;
SET ROLE txbits__owner;

-- TODO: delete test factory registration
COMMIT;

-- vi: expandtab ts=2 sw=2
