GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
  search.index_build, search.current_index, search.entry TO "${searchRuntimeRole}";
REVOKE ALL ON TABLE search.flyway_schema_history FROM "${searchRuntimeRole}";
