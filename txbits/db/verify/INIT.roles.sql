-- Verify txbits:INIT.roles on pg

BEGIN;
SET ROLE txbits__owner;

CREATE TABLE public.test_table();
SELECT ddl_tools.test_function(
  'bogus'
  , $$
BEGIN
  NULL;
END
$$);

ROLLBACK;

-- vi: expandtab ts=2 sw=2
