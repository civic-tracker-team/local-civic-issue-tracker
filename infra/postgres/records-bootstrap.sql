-- Run once in an empty development/test database as its provisioning administrator.
-- Role names and passwords are psql variables supplied outside version control.
CREATE ROLE :"migration_role" LOGIN PASSWORD :'migration_password';
CREATE ROLE :"runtime_role" LOGIN PASSWORD :'runtime_password';
CREATE SCHEMA records AUTHORIZATION :"migration_role";
REVOKE ALL ON SCHEMA records FROM PUBLIC;
GRANT USAGE ON SCHEMA records TO :"runtime_role";
-- Flyway grants runtime DML on application tables, excluding migration history.
