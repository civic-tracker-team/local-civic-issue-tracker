-- Provision an empty development/test database with externally supplied credentials.
CREATE ROLE :"migration_role" LOGIN PASSWORD :'migration_password';
CREATE ROLE :"runtime_role" LOGIN PASSWORD :'runtime_password';
CREATE SCHEMA analysis AUTHORIZATION :"migration_role";
REVOKE ALL ON SCHEMA analysis FROM PUBLIC;
GRANT USAGE ON SCHEMA analysis TO :"runtime_role";
-- Flyway grants runtime access to application tables, excluding migration history.
