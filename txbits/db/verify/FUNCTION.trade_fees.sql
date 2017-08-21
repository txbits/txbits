-- Verify txbits:FUNCTION.trade_fees on pg

BEGIN;
SET ROLE txbits__owner;

SELECT 'public.trade_fees(varchar,varchar)'::regprocedure;
ROLLBACK;

-- vi: expandtab ts=2 sw=2
