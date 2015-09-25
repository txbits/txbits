CREATE OR REPLACE FUNCTION _ddl_tools._test_function_strip_declare(
  function_name text
  , body text
  , level text DEFAULT 'WARNING'
) RETURNS text LANGUAGE plpgsql AS $body$
DECLARE
  /*
   * \A - Start of string
   * \s* - [:space:]*
   */
  c_regexp CONSTANT text := $$\A\s*declare$$;
BEGIN
  IF body ~* c_regexp THEN
    DECLARE
      m CONSTANT text := format( 'striping DECLARE from test function %s', function_name );
      d CONSTANT text := format( 'DECLARE match = "%s"', (regexp_matches( body, c_regexp, 'i' ))[1] );
      c_level CONSTANT text := UPPER( level );
    BEGIN
      CASE c_level
        WHEN 'DEBUG' THEN RAISE DEBUG '%', m USING DETAIL = d;
        WHEN 'LOG' THEN RAISE LOG '%', m USING DETAIL = d;
        WHEN 'INFO' THEN RAISE INFO '%', m USING DETAIL = d;
        WHEN 'NOTICE' THEN RAISE NOTICE '%', m USING DETAIL = d;
        WHEN 'WARNING' THEN RAISE WARNING '%', m USING DETAIL = d;
        WHEN 'EXCEPTION' THEN RAISE EXCEPTION '%', m USING DETAIL = d;
        ELSE RAISE 'Invalid level "%"', level;
      END CASE;
    END;
    RETURN regexp_replace(
      body
      , c_regexp
      , '-- WARNING: Stripped extraneous DECLARE'
      , 'i'
    );
  ELSE
    RETURN body;
  END IF;
END
$body$;
CREATE OR REPLACE FUNCTION _test_ddl_tools.test_function_strip_declare()
RETURNS SETOF text LANGUAGE plpgsql AS $body$
DECLARE
  s CONSTANT name := '_ddl_tools';
  f CONSTANT name := '_test_function_strip_declare';

  c_call CONSTANT text := format(
    $$SELECT %I.%I( 'function_name', %%L, %%L )$$
    , s, f
  );

  w CONSTANT text := '-- WARNING: Stripped extraneous DECLARE';

BEGIN
  RETURN NEXT is(
    _ddl_tools._test_function_strip_declare( 'fn', E'test 1\ntest2' )
    , E'test 1\ntest2'
    , 'No DECLARE returns raw string'
  );

  RETURN NEXT throws_ok(
    format( c_call, 'declare moo', 'exception' )
    , 'striping DECLARE from test function function_name'
    , 'Basic declare'
  );

  RETURN NEXT throws_ok(
    format( c_call, E'\n\tdeclare\n\tmoo', 'exception' )
    , 'striping DECLARE from test function function_name'
    , 'Check whitespace'
  );

  RETURN NEXT throws_ok(
    format( c_call, E'\n\tdEcLaRe\n\tmoo', 'exception' )
    , 'striping DECLARE from test function function_name'
    , 'Case insensitive'
  );

  RETURN NEXT is(
    _ddl_tools._test_function_strip_declare( 'fn', E'declare\t\nvar', 'DEBUG' )
    , w || E'\t\nvar'
    , 'Strip basic declare'
  );

  RETURN NEXT is(
    _ddl_tools._test_function_strip_declare( 'fn', E'\t\ndeclare\n\tvar', 'DEBUG' )
    , w || E'\n\tvar'
    , 'Strip whitespace'
  );

  RETURN NEXT is(
    _ddl_tools._test_function_strip_declare( 'fn', E'\t\ndeCLarE\n\tvar', 'DEBUG' )
    , w || E'\n\tvar'
    , 'Strip case insensitive'
  );

END
$body$;

CREATE OR REPLACE FUNCTION ddl_tools.test_function(
  function_name text
  , body text
) RETURNS void LANGUAGE plpgsql AS $body$
DECLARE
  c_declare CONSTANT text := format(
    $DECLARE_QUOTE$
DECLARE
  foid CONSTANT regprocedure := %L || '()';
  fs CONSTANT name := ( SELECT nspname FROM pg_proc p JOIN pg_namespace n ON n.oid = pronamespace WHERE p.oid = foid );
  bs CONSTANT name := regexp_replace( fs, '^_test_', '', 'i' );
  fn CONSTANT name := ( SELECT proname FROM pg_proc p WHERE p.oid = foid );
$DECLARE_QUOTE$
    , function_name
  );

  c_sql CONSTANT text := format(
$$CREATE OR REPLACE FUNCTION %s()
RETURNS SETOF text LANGUAGE plpgsql AS %L
$$
    , function_name
    , c_declare || _ddl_tools._test_function_strip_declare( function_name, body )
  );

  v_function_oid regproc;

  -- Note that you have to be SU to change log_min_messages, so skip it
  c_old_client CONSTANT text := current_setting( 'client_min_messages' );
BEGIN
  /*
   * Originally I tried doing this by looking at pg_proc.xmin, but catalog
   * xmin's don't follow normal rules. Track using a temp table instead.
   */
  -- Squelch NOTICE
  PERFORM pg_catalog.set_config( 'client_min_messages', 'ERROR', true );
  CREATE TEMP TABLE IF NOT EXISTS test_function__functions_created_this_transaction(
    function_oid regproc
    ) ON COMMIT DELETE ROWS -- Could have used DROP, but DELETE ROWS should reduce catalog bloat
  ;
  -- This GRANT creates a bogus WARNING
  GRANT ALL ON pg_temp.test_function__functions_created_this_transaction TO PUBLIC;
  PERFORM pg_catalog.set_config( 'client_min_messages', c_old_client, true );

  -- Don't try and parse function_name; just trap the error from regproc instead
  BEGIN
    v_function_oid := function_name::regproc;
  EXCEPTION
    WHEN undefined_function THEN
      NULL;
  END;

  IF EXISTS( SELECT 1 FROM pg_temp.test_function__functions_created_this_transaction WHERE function_oid = v_function_oid )
  THEN
    RAISE '% has already been defined in this transaction', function_name
      USING HINT = $$This probably means you are accidentally over-writing the function.$$
    ;
  END IF;

  PERFORM ddl_tools.exec( c_sql );
  INSERT INTO pg_temp.test_function__functions_created_this_transaction
    VALUES( function_name::regproc )
  ;
END
$body$;

-- Don't be cute and use test_funtion() to define this test...
-- TODO: Need to actually create a test function in a separate transaction and make sure we can over-write it
CREATE OR REPLACE FUNCTION _test_ddl_tools.test_function()
RETURNS SETOF text LANGUAGE plpgsql AS $body$
DECLARE
  c_test_call CONSTANT text := $TEST_CALL$
SELECT ddl_tools.test_function(
  '_test_ddl_tools._test_test_function'
  , $TEST_BODY$
DECLARE
  s CONSTANT name := '_test_ddl_tools';
  f CONSTANT name := '_test_test_function';
BEGIN
  RETURN NEXT is(
    foid
    , (s || '.' || f || '()')::regprocedure
    , 'foid set correctly'
  );
  RETURN NEXT is(
    fs
    , s
    , 'fs set correctly'
  );
  RETURN NEXT is(
    bs
    , 'ddl_tools'
    , 'bs set correctly'
  );
  RETURN NEXT is(
    fn
    , f
    , 'fn set correctly'
  );
END
$TEST_BODY$
);$TEST_CALL$;

BEGIN
  BEGIN
    RETURN NEXT ok(
      NOT EXISTS( SELECT 1
        FROM pg_temp.test_function__functions_created_this_transaction
        WHERE function_oid = '_test_ddl_tools._test_test_function'::regproc
      )
      , 'Test function is registered in tracking table'
    );
  EXCEPTION
    WHEN undefined_table THEN
      RETURN NEXT skip( 'Tracking table does not exist' );
  END;

  SET LOCAL client_min_messages = PANIC;
  RETURN NEXT lives_ok(
    c_test_call
    , 'Can create a test function'
  );

  RETURN NEXT ok(
    EXISTS( SELECT 1
      FROM pg_temp.test_function__functions_created_this_transaction
      WHERE function_oid = '_test_ddl_tools._test_test_function'::regproc
    )
    , 'Test function is registered in tracking table'
  );

--  SET LOCAL client_min_messages = debug;
  RETURN NEXT throws_ok(
    c_test_call
    , '_test_ddl_tools._test_test_function has already been defined in this transaction'
    , 'Duplicate call throws error'
  );
  SET client_min_messages = WARNING;

  RETURN NEXT cmp_ok(
    ( SELECT prosrc
        FROM pg_proc
        WHERE oid = '_test_ddl_tools._test_test_function()'::regprocedure
      )
    , '~~'
    , E'%\n-- WARNING: Stripped extraneous DECLARE\n  s CONSTANT name := %'
    , 'Function body has DECLARE warning'
  );

  RETURN QUERY SELECT _test_ddl_tools._test_test_function();

  RETURN NEXT lives_ok(
    $$DROP FUNCTION _test_ddl_tools._test_test_function()$$
    , 'Drop test function'
  );
END
$body$;

-- vi: expandtab ts=2 sw=2
