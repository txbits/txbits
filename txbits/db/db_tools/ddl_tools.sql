-- This is a db_tools file!

SELECT CASE WHEN :'DB_DIR' = '' THEN '.' ELSE :'DB_DIR' END AS DB_DIR
\gset

CREATE SCHEMA ddl_tools;
\i :DB_DIR/functions/ddl_tools.exec.sql

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

/*
 * This bit assumes that either session user is already a superuser, or that at
 * least the su role already exists (and is superuser) and the current user has
 * been granted access to su (either directly or via su_access).
 */
SELECT ddl_tools.role__create( 'su', 'SUPERUSER' );
SET ROLE su;
SELECT ddl_tools.role__create( 'su_access', 'NOINHERIT', 'su' );
SELECT ddl_tools.exec($$GRANT su_access TO SESSION_USER;$$)
  WHERE NOT pg_has_role(SESSION_USER,'su_access','usage')
;

-- Fix existing objects
ALTER SCHEMA ddl_tools OWNER TO su;
ALTER FUNCTION ddl_tools.exec(text, name) OWNER TO su;
ALTER FUNCTION ddl_tools.role__create(name, text, text) OWNER TO su;

CREATE SCHEMA _ddl_tools;
CREATE SCHEMA _test_ddl_tools;
\i :DB_DIR/functions/ddl_tools.schema__drop.sql
\i :DB_DIR/functions/ddl_tools.test_function.sql

-- vi: expandtab ts=2 sw=2
