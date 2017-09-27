-- Deploy txbits:INIT.pgtap to pg
-- requires: INIT.ddl_tools

BEGIN;
SET ROLE su;

CREATE SCHEMA tap;
GRANT USAGE ON SCHEMA tap TO public;
CREATE EXTENSION pgtap SCHEMA tap;
COMMIT;

-- vi: expandtab ts=2 sw=2
