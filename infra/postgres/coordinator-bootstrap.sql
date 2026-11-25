-- Provision an empty development/test database with externally supplied credentials.
CREATE ROLE :"migration_role" LOGIN PASSWORD :'migration_password';
CREATE ROLE :"runtime_role" LOGIN PASSWORD :'runtime_password';
CREATE SCHEMA coordinator AUTHORIZATION :"migration_role";
REVOKE ALL ON SCHEMA coordinator FROM PUBLIC;
GRANT USAGE ON SCHEMA coordinator TO :"runtime_role";
-- Flyway grants runtime access to application tables, excluding migration history.
