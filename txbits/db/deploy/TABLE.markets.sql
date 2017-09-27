-- Deploy txbits:TABLE.markets.sql to pg
-- requires: 2

BEGIN;
SET ROLE su;

-- TODO: Instead of this, add the application user to txbits__app and change the OWNER to txbits__owner
GRANT ALL ON public.markets TO txbits__owner;

GRANT ALL ON public.market_id_seq  TO txbits__owner;
ALTER SEQUENCE public.market_id_seq OWNED BY public.markets.id;

-- TODO: uncommend after above is fixed.. (only an owner can alter :() SET ROLE txbits__owner;

-- Allow for per-market fee configuration
ALTER TABLE public.markets
  ADD fee_linear numeric(23,8) CONSTRAINT markets_fee_linear_check CHECK(fee_linear IS NULL OR fee_linear >= 0)
  , ADD fee_one_way boolean
;

SET ROLE txbits__owner;
SELECT tf.register(
  'public.markets'
  , array[
    row(
      'base'
      , $tf$
      INSERT INTO public.markets (base, counter, limit_min, position)
        SELECT 'TST1', 'TST2', 1, -1
          /*
           * Normally this would actually *use* the results of tf.get(). In
           * this case, we just hard-code TST1 and TST2, BUT we still have to
           * ensure that the test currencies are there. This WHERE clause does
           * that.
           *
           * NOTE: This is intentionally coded to attempt the insert no matter
           * what comes back from tf.get()!
           */
          WHERE (SELECT count(*) FROM tf.get(NULL::public.currencies,'base')) > -1
        RETURNING *
      $tf$
    )::tf.test_set
    , row(
      'fee override'
      , $tf$
      INSERT INTO public.markets (base, counter, limit_min, position, fee_linear, fee_one_way)
        SELECT
            'TST2', 'TST1' -- a bit bogus, but works for now..
            , 1, -1
            -- Make sure market limits are different
            , trade_fees.linear + 1
            , NOT trade_fees.one_way
          FROM tf.get(NULL::public.trade_fees, 'base') AS trade_fees
          /*
           * Normally this would actually *use* the results of tf.get(). In
           * this case, we just hard-code TST1 and TST2, BUT we still have to
           * ensure that the test currencies are there. This WHERE clause does
           * that.
           *
           * NOTE: This is intentionally coded to attempt the insert no matter
           * what comes back from tf.get()!
           */
          WHERE (SELECT count(*) FROM tf.get(NULL::public.currencies,'base')) > -1
        RETURNING *
      $tf$
    )::tf.test_set
  ]
);

SELECT ddl_tools.test_function(
  '_test_public.markets'
  , $body$
  s CONSTANT name = bs;
	t CONSTANT name = fn;
  table_full_name CONSTANT text = format('%I.%I', s, t);

BEGIN
  -- For now, just verify the test data works...
  RETURN NEXT tf.tap(table_full_name);
  RETURN NEXT tf.tap(table_full_name, 'fee override');
END
$body$
);

COMMIT;

-- vi: expandtab ts=2 sw=2
