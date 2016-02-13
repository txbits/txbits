-- This is a db_tools file!

CREATE OR REPLACE FUNCTION cat_tools.enum_range_srf(
  enum regtype
) RETURNS SETOF text LANGUAGE sql AS $body$
SELECT * FROM unnest( cat_tools.enum_range($1) ) AS r(enum_label)
$body$;

SELECT ddl_tools.test_function(
  '_test_cat_tools.enum_range_srf'
  , $body$
  c_test_enum CONSTANT text := '_cat_tools.enum_range_srf_test_enum';
BEGIN
  RETURN NEXT lives_ok(
    $$CREATE TYPE $$ || c_test_enum || $$ AS ENUM( 'ZZZ Label 1', 'Label 2' )$$
    , 'Create test enum'
  );

  RETURN NEXT results_eq(
    format( 'SELECT cat_tools.enum_range_srf( %L )', c_test_enum )
    , $$VALUES ( 'ZZZ Label 1' ), ( 'Label 2' )$$
  );

  RETURN NEXT lives_ok(
    'DROP TYPE ' || c_test_enum
    , 'Drop test enum'
  );
END
$body$
);

-- vi: expandtab ts=2 sw=2
