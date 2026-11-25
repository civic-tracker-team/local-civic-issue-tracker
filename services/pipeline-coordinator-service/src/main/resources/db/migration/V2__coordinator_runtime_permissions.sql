-- Runtime writes stay within coordinator-owned tables, excluding Flyway history.
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
  coordinator.run, coordinator.run_slot, coordinator.run_meeting,
  coordinator.run_input, coordinator.run_event TO "${coordinatorRuntimeRole}";
GRANT SELECT, INSERT ON TABLE
  coordinator.publication, coordinator.publication_range,
  coordinator.publication_meeting, coordinator.publication_source TO "${coordinatorRuntimeRole}";
GRANT SELECT, UPDATE ON TABLE coordinator.current_publication TO "${coordinatorRuntimeRole}";
REVOKE ALL ON TABLE coordinator.flyway_schema_history FROM "${coordinatorRuntimeRole}";
