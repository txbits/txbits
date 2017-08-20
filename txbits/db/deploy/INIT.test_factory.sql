-- Deploy txbits:INIT.test_factory to pg
-- requires: INIT.pgtap

BEGIN;
SET ROLE su;

SET search_path = "$user", public, tap; -- Make sure pgtap is in our search path...
CREATE EXTENSION test_factory;
CREATE EXTENSION test_factory_pgtap;
COMMIT;

-- vi: expandtab ts=2 sw=2
