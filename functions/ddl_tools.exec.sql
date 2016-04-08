-- This is a db_tools file!

CREATE FUNCTION ddl_tools.exec(
  sql text
  , as_role name DEFAULT NULL
) RETURNS void LANGUAGE plpgsql AS $body$
DECLARE
  ci_orig_user CONSTANT name := quote_ident(current_user);
BEGIN
  IF as_role IS NOT NULL THEN
    RAISE DEBUG 'EXECUTE AS %: %', as_role, sql;
    EXECUTE 'SET ROLE ' || quote_ident( as_role );
    EXECUTE sql;
    EXECUTE 'SET ROLE ' || ci_orig_user;
  ELSE
    RAISE DEBUG 'EXECUTE: %', sql;
    EXECUTE sql;
  END IF;
END
$body$;
CREATE FUNCTION ddl_tools.exec(
  sql text[]
  , as_role name DEFAULT NULL
) RETURNS void LANGUAGE plpgsql AS $body$
DECLARE
  v_sql text;
BEGIN
  FOREACH v_sql IN ARRAY sql LOOP
    PERFORM ddl_tools.exec( v_sql, as_role );
  END LOOP;
END
$body$;

-- vi: expandtab ts=2 sw=2
