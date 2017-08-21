-- Deploy txbits:TABLE.trade_fees.sql to pg
-- requires: 2

BEGIN;
--SET ROLE txbits__owner;

ALTER TABLE trade_fees
  ADD one_row_only boolean NOT NULL DEFAULT true
    CONSTRAINT trade_fees__pk_one_row_only PRIMARY KEY
    CONSTRAINT trade_fees_may_only_have_on_row CHECK(one_row_only)
;

CREATE OR REPLACE FUNCTION _test_public.__trade_fees__set(
  linear public.trade_fees.linear%TYPE
  , one_way public.trade_fees.one_way%TYPE
) RETURNS void LANGUAGE sql AS $body$
INSERT INTO public.trade_fees(linear, one_way)
  VALUES($1, $2)
  ON CONFLICT (one_row_only) DO UPDATE
    SET
      linear = $1
      , one_way = $2
;
$body$;
COMMENT ON FUNCTION  _test_public.__trade_fees__set(
  linear public.trade_fees.linear%TYPE
  , one_way public.trade_fees.one_way%TYPE
) IS $$A simple helper function to ensure there is a value in the trade_fees table. NOT MEANT FOR PRODUCTION USE.$$;

SELECT ddl_tools.test_function(
  '_test_public.trade_fees'
  , $body$
	s CONSTANT name = bs;
	t CONSTANT name = fn;

  update_template CONSTANT text = format(
    $$UPDATE %I.%I SET one_row_only = %%L$$ -- Remember %% for use of later format's!
    , s, t
  );
BEGIN
  -- Ensure there's already a row in the table
  PERFORM _test_public.__trade_fees__set(.42,false);

  RETURN NEXT throws_ok(
    format( $$INSERT INTO %I.%I VALUES(1,true)$$, s, t )
    , '23505' -- Duplicate key value
    , 'duplicate key value violates unique constraint "trade_fees__pk_one_row_only"'
    , 'inserting additional row fails'
  );

  RETURN NEXT throws_ok(
    format(update_template, null)
    , '23502'
    , 'null value in column "one_row_only" violates not-null constraint'
    , 'Setting one_row to NULL fails'
  );

  RETURN NEXT throws_ok(
    format(update_template, FALSE)
    , '23514'
    , 'new row for relation "trade_fees" violates check constraint "trade_fees_may_only_have_on_row"'
    , 'Setting one_row to FALSE fails'
  );
END
$body$
);

COMMIT;

-- vi: expandtab ts=2 sw=2
