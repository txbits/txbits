-- Verify txbits:INIT.ddl_tools.sql on pg

BEGIN;
--SET ROLE txbits__owner;

SELECT 'ddl_tools.role__create'::regproc;
ROLLBACK;

-- vi: expandtab ts=2 sw=2
