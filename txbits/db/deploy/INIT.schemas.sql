-- Deploy txbits:INIT.schemas to pg
-- requires: INIT.roles

BEGIN;
SET ROLE su; -- Or could be database owner...

-- Normally this would be handled by ddl_tools.create_schema()
CREATE SCHEMA _public AUTHORIZATION txbits__owner; -- 'private' schema
CREATE SCHEMA _test_public AUTHORIZATION txbits__owner; -- test schema
CREATE SCHEMA _test__public AUTHORIZATION txbits__owner; -- test schema for _public

COMMIT;

-- vi: expandtab ts=2 sw=2
