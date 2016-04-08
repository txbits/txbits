CREATE OR REPLACE FUNCTION ddl_tools.schema__drop(
    schema_name name
) RETURNS void LANGUAGE sql SET client_min_messages = 'WARNING' AS $body$
SELECT ddl_tools.exec( format( 'DROP SCHEMA IF EXISTS %I', prefix || schema_name
    FROM unnest('{"",_,_test_,_test__}'::name[]) AS prefix
$body$;

-- vi: expandtab ts=2 sw=2
