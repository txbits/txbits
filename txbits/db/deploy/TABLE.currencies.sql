-- Deploy txbits:TABLE.currencies to pg
-- requires: INIT.roles

BEGIN;
SET ROLE su;

-- TODO: Instead of this, add the application user to txbits__app and change the OWNER to txbits__owner
GRANT ALL ON public.currencies TO txbits__owner;

SET ROLE txbits__owner;

SELECT tf.register(
  'public.currencies'
  , array[
    row(
      'base'
      , $$
      INSERT INTO public.currencies(currency, position) VALUES
        ('TST1', 999)
        , ('TST2', 999)
        RETURNING *
      $$
    )::tf.test_set
  ]
);

SELECT ddl_tools.test_function(
  '_test_public.currencies'
  , $body$
  s CONSTANT name = bs;
	t CONSTANT name = fn;
  table_full_name CONSTANT text = format('%I.%I', s, t);

BEGIN
  -- For now, just verify the test data works...
  RETURN NEXT tf.tap(table_full_name);
END
$body$
);

COMMIT;

-- vi: expandtab ts=2 sw=2
