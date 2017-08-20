-- Deploy txbits:INIT.roles to pg
-- requires: INIT.ddl_tools

BEGIN;
SET ROLE su;

-- Non-login roles
SELECT ddl_tools.role__create( 'txbits__' || suffix )
	FROM unnest( '{owner, read, read_pii, app, dev}'::text[] ) AS suffix
;
GRANT txbits__read TO txbits__read_pii;
GRANT txbits__read_pii TO txbits__app;

-- Login roles
/*
SELECT ddl_tools.role__create( 'txbits_frontend', $opt$LOGIN PASSWORD '' $opt$ );
SELECT ddl_tools.role__create( 'txbits_portal', $opt$LOGIN PASSWORD '' $opt$ );
GRANT txbits__app TO txbits_frontend, txbits_portal;
*/

COMMIT;

-- vi: expandtab ts=2 sw=2
