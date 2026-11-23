GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
  analysis.analysis_operation, analysis.operation_slot TO "${analysisRuntimeRole}";
GRANT SELECT, INSERT ON TABLE
  analysis.meeting_analysis, analysis.prior_context, analysis.analysis_issue,
  analysis.issue_mention, analysis.development, analysis.development_date,
  analysis.evidence_span, analysis.development_evidence, analysis.association,
  analysis.association_evidence TO "${analysisRuntimeRole}";
REVOKE ALL ON TABLE analysis.flyway_schema_history FROM "${analysisRuntimeRole}";
