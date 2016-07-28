CREATE OR REPLACE FUNCTION ddl_tools.schema__drop(
    schema_name name
) RETURNS void LANGUAGE plpgsql SET client_min_messages = 'WARNING' AS $body$
DECLARE
  prefix name;
BEGIN
  FOREACH prefix IN ARRAY '{"",_,_test_,_test__}'::name[] LOOP
    PERFORM ddl_tools.exec( format( 'DROP SCHEMA IF EXISTS %I', prefix || schema_name ) );
  END LOOP;
END
$body$;

-- vi: expandtab ts=2 sw=2
