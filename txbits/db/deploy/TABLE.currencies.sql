-- Deploy txbits:TABLE.currencies to pg
-- requires: INIT.roles

BEGIN;
SET ROLE su;

-- TODO: Instead of this, add the application user to txbits__app and change the OWNER to txbits__owner
GRANT ALL ON currencies TO txbits__owner;

SET ROLE txbits__owner;

SELECT tf.register(
  'currencies'
  , array[
    row(
      'base'
      , $$
      INSERT INTO currencies(currency, position) VALUES
        ('TST1', 999)
        , ('TST2', 999)
        RETURNING *
      $$
    )::tf.test_set
  ]
);
COMMIT;

-- vi: expandtab ts=2 sw=2
