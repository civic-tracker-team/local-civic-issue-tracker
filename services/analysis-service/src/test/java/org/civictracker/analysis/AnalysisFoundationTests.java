package org.civictracker.analysis;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.civictracker.analysis.persistence.AnalysisRevision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AnalysisFoundationTests {
  static final List<String> FINDINGS =
      List.of(
          "meeting_analysis",
          "prior_context",
          "analysis_issue",
          "issue_mention",
          "development",
          "development_date",
          "evidence_span",
          "development_evidence",
          "association",
          "association_evidence");
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired PlatformTransactionManager transactions;

  static AnalysisRevision revision() {
    var r = new AnalysisRevision();
    r.origin = AnalysisRevision.Origin.IMPORTED;
    r.meetingId = UUID.randomUUID();
    r.officialId = "synthetic";
    r.meetingDate = LocalDate.of(2026, 1, 15);
    r.title = "Synthetic meeting";
    r.contractRevision = "grounded-findings-3";
    r.contractDetailJson = "{}";
    r.profileFingerprint = "a".repeat(64);
    r.inputSha256 = "b".repeat(64);
    r.modelInputSha256 = "c".repeat(64);
    r.candidatesSha256 = "d".repeat(64);
    r.receiptSha256 = "e".repeat(64);
    r.promptTokens = 12;
    r.artifactKey = "synthetic/" + r.id;
    r.createdAt = Instant.now();
    return r;
  }

  @Test
  void migrationAndAssignedIdentityRoundTrip() {
    var r = revision();
    assertTrue(r.isNew());
    assertEquals(4, r.id.version());
    new TransactionTemplate(transactions)
        .executeWithoutResult(status -> {
          entities.persist(r);
          entities.flush();
          assertFalse(r.isNew());
          entities.clear();
          var loaded = entities.find(AnalysisRevision.class, r.id);
          assertFalse(loaded.isNew());
          assertEquals(r.meetingId, loaded.meetingId);
          assertEquals(r.inputSha256, loaded.inputSha256);
        });
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from analysis.operation_slot where id=1", Integer.class));
    assertEquals(11, entities.getMetamodel().getEntities().size());
  }

  @Test
  void runtimeFindingsAreInsertOnly() {
    var r = revision();
    new TransactionTemplate(transactions)
        .executeWithoutResult(s -> {
          entities.persist(r);
          entities.flush();
        });
    for (String table : FINDINGS) {
      assertEquals(
          true,
          jdbc.queryForObject(
              "select has_table_privilege(current_user, ?, 'SELECT,INSERT')",
              Boolean.class,
              "analysis." + table));
      assertThrows(
          DataAccessException.class, () -> jdbc.execute("update analysis." + table + " set id=id"));
      assertThrows(DataAccessException.class, () -> jdbc.execute("delete from analysis." + table));
    }
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from analysis.meeting_analysis where id=?", Integer.class, r.id));
  }

  @Test
  void runtimeCannotAccessRecordsHistoryOrDdl() {
    assertThrows(
        DataAccessException.class, () -> jdbc.queryForList("select * from records.source_meeting"));
    assertThrows(
        DataAccessException.class,
        () -> jdbc.queryForList("select * from analysis.flyway_schema_history"));
    assertThrows(
        DataAccessException.class,
        () -> jdbc.execute("update analysis.flyway_schema_history set success=false"));
    assertThrows(
        DataAccessException.class,
        () -> jdbc.execute("create table analysis.forbidden (id integer)"));
    assertEquals(
        false,
        jdbc.queryForObject(
            "select has_schema_privilege(current_user, 'records', 'USAGE')", Boolean.class));
  }

  @Test
  void operationRuntimeCanWriteAndConstraintsRejectInvalidValues() {
    var id = UUID.randomUUID();
    jdbc.update(
        "insert into analysis.analysis_operation (id,state,input_json,command_sha256,accepted_at)"
            + " values (?,'ACCEPTED','{}',?,now())",
        id,
        "f".repeat(64));
    jdbc.update(
        "update analysis.analysis_operation set state='RUNNING',started_at=now() where id=?", id);
    assertEquals(
        "RUNNING",
        jdbc.queryForObject(
            "select state from analysis.analysis_operation where id=?", String.class, id));
    assertThrows(
        DataAccessException.class,
        () -> jdbc.update("update analysis.analysis_operation set state='BAD' where id=?", id));
    assertThrows(
        DataAccessException.class,
        () ->
            jdbc.update(
                "update analysis.analysis_operation set command_sha256='bad' where id=?", id));
    assertThrows(
        DataAccessException.class,
        () ->
            jdbc.update(
                "update analysis.analysis_operation set revision_id=? where id=?",
                UUID.randomUUID(),
                id));
    assertThrows(
        DataAccessException.class,
        () -> jdbc.execute("insert into analysis.operation_slot(id) values (2)"));
    jdbc.update("delete from analysis.analysis_operation where id=?", id);
  }
}
