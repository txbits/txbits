CREATE SCHEMA tap;
GRANT USAGE ON SCHEMA tap TO public;
CREATE EXTENSION pgtap SCHEMA tap;
SET search_path = "$user", public, tap;
CREATE EXTENSION test_factory;
CREATE EXTENSION test_factory_pgtap;

-- vi: noexpandtab sw=4 ts=4
