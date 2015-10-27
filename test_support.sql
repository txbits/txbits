CREATE SCHEMA tap;
GRANT USAGE ON SCHEMA tap TO public;
CREATE EXTENSION pgtap SCHEMA tap;
SET search_path = "$user", public, tap;
CREATE EXTENSION test_factory;

-- vi: noexpandtab sw=4 ts=4
