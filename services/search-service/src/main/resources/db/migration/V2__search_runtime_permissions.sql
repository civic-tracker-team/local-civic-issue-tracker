GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE search.index_build, search.entry
  TO "${searchRuntimeRole}";
GRANT SELECT, UPDATE ON TABLE search.current_index TO "${searchRuntimeRole}";
REVOKE ALL ON TABLE search.flyway_schema_history FROM "${searchRuntimeRole}";
