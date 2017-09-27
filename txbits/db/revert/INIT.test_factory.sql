-- Revert txbits:INIT.test_factory from pg

BEGIN;
SET ROLE su;

DROP EXTENSION test_factory_pgtap;
DROP EXTENSION test_factory;
COMMIT;

-- vi: expandtab ts=2 sw=2
