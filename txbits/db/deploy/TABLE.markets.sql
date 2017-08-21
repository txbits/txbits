-- Deploy txbits:TABLE.markets.sql to pg
-- requires: 2

BEGIN;
--SET ROLE txbits__owner;

-- Allow for per-market fee configuration
ALTER TABLE markets
  ADD fee_linear numeric(23,8) CONSTRAINT markets_fee_linear_check CHECK(fee_linear IS NULL OR fee_linear >= 0)
  , ADD fee_one_way boolean
;

SELECT tf.register(
  'markets'
  , array[
    row(
      'base'
      , $tf$
      INSERT INTO markets (base, counter, limit_min, position)
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
          WHERE (SELECT count(*) FROM tf.get(NULL::currencies,'base')) > -1
        RETURNING *
      $tf$
    )::tf.test_set
    , row(
      'fee override'
      , $tf$
      INSERT INTO markets (base, counter, limit_min, position, fee_linear, fee_one_way)
        SELECT
            'TST2', 'TST1' -- a bit bogus, but works for now..
            , 1, -1
            -- Make sure market limits are different
            , trade_fees.linear + 1
            , NOT trade_fees.one_way
          FROM tf.get(NULL::trade_fees, 'base') AS trade_fees
          /*
           * Normally this would actually *use* the results of tf.get(). In
           * this case, we just hard-code TST1 and TST2, BUT we still have to
           * ensure that the test currencies are there. This WHERE clause does
           * that.
           *
           * NOTE: This is intentionally coded to attempt the insert no matter
           * what comes back from tf.get()!
           */
          WHERE (SELECT count(*) FROM tf.get(NULL::currencies,'base')) > -1
        RETURNING *
      $tf$
    )::tf.test_set
  ]
);
    
COMMIT;

-- vi: expandtab ts=2 sw=2
