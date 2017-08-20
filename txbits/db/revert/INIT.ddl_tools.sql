-- Revert txbits:INIT.ddl_tools.sql from pg

BEGIN;
--SET ROLE txbits__owner;

SET client_min_messages = WARNING; -- squelch noise

DROP SCHEMA _test_ddl_tools CASCADE;
DROP SCHEMA _ddl_tools CASCADE;
DROP SCHEMA ddl_tools CASCADE;

COMMIT;

-- vi: expandtab ts=2 sw=2
