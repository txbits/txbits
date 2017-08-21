-- Revert txbits:INIT.schemas from pg

BEGIN;
SET ROLE su;

DROP SCHEMA _public; -- 'private' schema
DROP SCHEMA _test_public; -- test schema
DROP SCHEMA _test__public; -- test schema for _public
COMMIT;

-- vi: expandtab ts=2 sw=2
