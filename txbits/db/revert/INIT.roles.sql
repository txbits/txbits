-- Revert txbits:INIT.roles from pg

BEGIN;
-- None, because roles are cluster-wide so dropping them can be problematic
COMMIT;

-- vi: expandtab ts=2 sw=2
