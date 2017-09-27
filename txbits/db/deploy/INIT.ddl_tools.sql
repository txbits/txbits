-- Deploy txbits:INIT.ddl_tools.sql to pg
-- requires: 5

BEGIN;
--SET ROLE txbits__owner;

\i :DB_DIR/ddl_tools.sql
COMMIT;

-- vi: expandtab ts=2 sw=2
