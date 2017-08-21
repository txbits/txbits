-- Deploy txbits:INIT.roles to pg
-- requires: INIT.ddl_tools

BEGIN;
SET ROLE su;

-- Non-login roles
SELECT ddl_tools.role__create( 'txbits__' || suffix )
	FROM unnest( '{owner, read, app, dev}'::text[] ) AS suffix
;

SET ROLE su;

-- TODO: Instead of this, add the application user to txbits__app and change the OWNER to txbits__owner
GRANT ALL ON SCHEMA public TO txbits__owner;

/*
 * NOTE: this is normally done by some functions that make it easy to create
 * new schemas, so some of it's a bit redundant.
 */
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO txbits__read, txbits__dev;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO txbits__app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE ON SEQUENCES TO txbits__app;

-- Login roles
/*
SELECT ddl_tools.role__create( 'txbits_frontend', $opt$LOGIN PASSWORD '' $opt$ );
SELECT ddl_tools.role__create( 'txbits_portal', $opt$LOGIN PASSWORD '' $opt$ );
GRANT txbits__app TO txbits_frontend, txbits_portal;
*/

COMMIT;

-- vi: expandtab ts=2 sw=2
