-- Revert txbits:TABLE.currencies from pg

BEGIN;
SET ROLE txbits__owner;

DROP FUNCTION _test_public.currencies();

-- TODO: delete test factory registration
COMMIT;

-- vi: expandtab ts=2 sw=2
