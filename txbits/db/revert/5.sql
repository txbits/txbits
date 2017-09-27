-- Revert txbits:5 from pg

BEGIN;
--SET ROLE txbits__owner;

alter table balances reset (fillfactor);
alter table orders reset (fillfactor);
COMMIT;

-- vi: expandtab ts=2 sw=2
