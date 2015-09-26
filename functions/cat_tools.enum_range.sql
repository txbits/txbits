CREATE OR REPLACE FUNCTION cat_tools.enum_range(
    enum regtype
) RETURNS text[] LANGUAGE plpgsql STABLE AS $body$
DECLARE
  ret text[];
BEGIN
  EXECUTE format('SELECT pg_catalog.enum_range( NULL::%s )', enum) INTO ret;
  RETURN ret;
END
$body$;

/*
 * NOTE: See cat_tools.enum_range_srf for unit test.
 */

-- vi: expandtab sw=2 ts=2
