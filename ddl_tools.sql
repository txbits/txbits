-- This is a db_tools file!

CREATE SCHEMA ddl_tools;
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

-- This is a db_tools file!

CREATE FUNCTION ddl_tools.role__create(
  role_name  name
  , options  text DEFAULT ''
  , in_roles  text DEFAULT NULL
) RETURNS void LANGUAGE plpgsql AS $f$
DECLARE
  c_current_roles CONSTANT text :=
    (SELECT array_to_string( array(
      SELECT quote_ident(b.rolname)
        FROM pg_catalog.pg_roles r
          JOIN pg_catalog.pg_auth_members m ON (m.member = r.oid)
          JOIN pg_catalog.pg_roles b ON (m.roleid = b.oid)
        WHERE r.rolname = role__create.role_name
      )
      , ', '
    ) )
  ;
BEGIN
  BEGIN
    PERFORM ddl_tools.exec( format( $$CREATE ROLE %I %s$$, role_name, options ) );
  EXCEPTION
    WHEN duplicate_object THEN
      PERFORM ddl_tools.exec( format( $$ALTER ROLE %I %s$$, role_name, options ) );
  END;

  -- Remove all existing role grants
  IF c_current_roles <> '' THEN
    PERFORM ddl_tools.exec( format(
      $$REVOKE %s FROM %I$$
      , c_current_roles
      , role_name
    ) );
  END IF;
  IF in_roles IS NOT NULL THEN
    PERFORM ddl_tools.exec( format( $$GRANT %s TO %I$$, in_roles, role_name ) );
  END IF;
END
$f$;


-- This is a db_tools file!


SELECT ddl_tools.role__create( 'su', 'SUPERUSER' );
SET ROLE su;
SELECT ddl_tools.role__create( 'su_access', 'NOINHERIT', 'su' );

-- Fix existing objects
ALTER SCHEMA ddl_tools OWNER TO su;
ALTER FUNCTION ddl_tools.exec(text, name) OWNER TO su;
ALTER FUNCTION ddl_tools.role__create(name, text, text) OWNER TO su;

CREATE SCHEMA _ddl_tools;
CREATE SCHEMA _test_ddl_tools;
\i functions/ddl_tools.test_function.sql

-- vi: expandtab ts=2 sw=2
