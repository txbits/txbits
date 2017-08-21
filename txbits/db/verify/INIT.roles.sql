-- Verify txbits:INIT.roles on pg

BEGIN;
SET ROLE txbits__owner;

CREATE TABLE public.test_table();
ROLLBACK;

-- vi: expandtab ts=2 sw=2
