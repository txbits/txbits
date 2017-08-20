-- Verify txbits:INIT.test_factory on pg

BEGIN;
SET ROLE su;

SELECT 'tf.get'::regproc;
ROLLBACK;

-- vi: expandtab ts=2 sw=2
