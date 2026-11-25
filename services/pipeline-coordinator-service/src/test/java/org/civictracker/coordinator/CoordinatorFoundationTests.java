package org.civictracker.coordinator;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.civictracker.coordinator.config.CoordinatorSettings;
import org.civictracker.coordinator.persistence.CurrentPublicationEntity;
import org.civictracker.coordinator.persistence.PublicationEntity;
import org.civictracker.coordinator.persistence.PublicationMeetingEntity;
import org.civictracker.coordinator.persistence.PublicationRangeEntity;
import org.civictracker.coordinator.persistence.PublicationSourceEntity;
import org.civictracker.coordinator.persistence.RunEntity;
import org.civictracker.coordinator.persistence.RunEventEntity;
import org.civictracker.coordinator.persistence.RunInputEntity;
import org.civictracker.coordinator.persistence.RunMeetingEntity;
import org.civictracker.coordinator.persistence.RunSlotEntity;
import org.civictracker.coordinator.publication.Disposition;
import org.civictracker.coordinator.run.ProgressState;
import org.civictracker.coordinator.run.RunState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class CoordinatorFoundationTests {
  @Autowired EntityManager entities;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;

  static RunEntity run() {
    var r = new RunEntity();
    r.commandSha256 = "a".repeat(64);
    r.sourceId = "synthetic";
    r.rangeFrom = LocalDate.of(2026, 1, 1);
    r.rangeTo = LocalDate.of(2026, 1, 31);
    r.committeeIds = "[\"council\"]";
    r.maxMeetings = 2;
    r.preparationProfileId = "synthetic-profile";
    r.state = RunState.ACTIVE;
    r.acceptedBy = "synthetic-maintainer";
    r.acceptedAt = Instant.parse("2026-01-01T00:00:00Z");
    return r;
  }

  static RunMeetingEntity meeting(RunEntity r) {
    var m = new RunMeetingEntity();
    m.id = new RunMeetingEntity.Key(r.id, 0);
    m.meetingId = UUID.randomUUID();
    m.officialId = "synthetic-meeting";
    m.meetingDate = r.rangeFrom;
    m.title = "Synthetic meeting";
    m.progress = ProgressState.DISCOVERED;
    return m;
  }

  @Test
  void migrationsRoundTripUuidAndCompositeIdentitiesAndFrozenRequest() {
    var r = run();
    var m = meeting(r);
    m.progress = ProgressState.PREPARED;
    m.frozenRequestJson = "{\"id\":\"synthetic\"}";
    m.frozenRequestSha256 = "b".repeat(64);
    var pin = new RunInputEntity();
    pin.id = new RunInputEntity.Key(r.id, 0, UUID.randomUUID());
    pin.documentId = UUID.randomUUID();
    pin.versionId = UUID.randomUUID();
    pin.artifactId = UUID.randomUUID();
    pin.sha256 = "c".repeat(64);
    pin.byteLength = 7;
    pin.preparationId = UUID.randomUUID();
    pin.profileFingerprint = "d".repeat(64);
    var p = new PublicationEntity();
    p.runId = r.id;
    p.publishedAt = r.acceptedAt;
    var range = new PublicationRangeEntity();
    range.id = new PublicationRangeEntity.Key(p.id, r.rangeFrom);
    range.rangeTo = r.rangeTo;
    var pm = new PublicationMeetingEntity();
    pm.id = new PublicationMeetingEntity.Key(p.id, m.meetingId);
    pm.officialId = m.officialId;
    pm.meetingDate = m.meetingDate;
    pm.title = m.title;
    pm.disposition = Disposition.ANALYZED;
    pm.revisionId = UUID.randomUUID();
    var source = new PublicationSourceEntity();
    source.id = new PublicationSourceEntity.Key(p.id, m.meetingId, pin.versionId);
    source.documentId = pin.documentId;
    var event = new RunEventEntity();
    event.runId = r.id;
    event.kind = RunEventEntity.Kind.STARTED;
    event.maintainer = r.acceptedBy;
    event.at = r.acceptedAt;
    assertTrue(r.isNew());
    assertTrue(m.isNew());
    assertEquals(4, r.id.version());
    new TransactionTemplate(transactions)
        .executeWithoutResult(tx -> {
          for (var row : List.of(r, m, pin, p, range, pm, source, event)) {
            entities.persist(row);
            entities.flush();
          }
          assertFalse(r.isNew());
          assertFalse(m.isNew());
          r.state = RunState.READY;
          entities.flush();
          assertEquals(1, r.version);
          entities.clear();
          var loaded = entities.find(RunEntity.class, r.id);
          assertFalse(loaded.isNew());
          assertEquals(RunState.READY, loaded.state);
          var frozen = entities.find(RunMeetingEntity.class, m.id);
          assertFalse(frozen.isNew());
          assertEquals(m.frozenRequestJson, frozen.frozenRequestJson);
          assertEquals(m.frozenRequestSha256, frozen.frozenRequestSha256);
          assertEquals(pin.versionId, entities.find(RunInputEntity.class, pin.id).versionId);
          assertEquals(
              pm.revisionId, entities.find(PublicationMeetingEntity.class, pm.id).revisionId);
          assertEquals(
              pin.documentId, entities.find(PublicationSourceEntity.class, source.id).documentId);
          assertEquals(
              range.rangeTo, entities.find(PublicationRangeEntity.class, range.id).rangeTo);
          assertEquals(p.runId, entities.find(PublicationEntity.class, p.id).runId);
          assertEquals(event.kind, entities.find(RunEventEntity.class, event.id).kind);
          assertNotNull(entities.find(RunSlotEntity.class, 1));
          assertNotNull(entities.find(CurrentPublicationEntity.class, 1));
        });
    assertEquals(10, entities.getMetamodel().getEntities().size());
  }

  @Test
  void storedInputPositionSurvivesReloadIndependentOfItemId() {
    var r = run();
    var m = meeting(r);
    var ids = List.of(new UUID(0, 2), new UUID(0, 1));
    new TransactionTemplate(transactions)
        .executeWithoutResult(tx -> {
          entities.persist(r);
          entities.persist(m);
          for (int position = 0; position < ids.size(); position++) {
            var pin = new RunInputEntity();
            pin.id = new RunInputEntity.Key(r.id, 0, ids.get(position));
            pin.inputOrder = position;
            pin.documentId = UUID.randomUUID();
            pin.versionId = UUID.randomUUID();
            pin.artifactId = UUID.randomUUID();
            pin.sha256 = "a".repeat(64);
            pin.byteLength = 1;
            pin.preparationId = UUID.randomUUID();
            pin.profileFingerprint = "b".repeat(64);
            entities.persist(pin);
          }
          entities.flush();
          entities.clear();
          var loaded =
              entities
                  .createQuery(
                      "from RunInputEntity where id.runId=:run order by inputOrder",
                      RunInputEntity.class)
                  .setParameter("run", r.id)
                  .getResultList();
          assertEquals(ids, loaded.stream().map(p -> p.id.acquisitionItemId()).toList());
        });
  }

  @Test
  void runtimePermissionsMatchMutableRunsAndImmutablePublications() {
    for (String table : List.of("run", "run_meeting", "run_input", "run_event", "run_slot")) {
      for (String permission : List.of("SELECT", "INSERT", "UPDATE", "DELETE")) {
        assertPrivilege(table, permission, true);
      }
    }
    for (String table :
        List.of("publication", "publication_range", "publication_meeting", "publication_source")) {
      assertPrivilege(table, "SELECT", true);
      assertPrivilege(table, "INSERT", true);
      assertPrivilege(table, "UPDATE", false);
      assertPrivilege(table, "DELETE", false);
      assertThrows(
          DataAccessException.class, () -> jdbc.execute("delete from coordinator." + table));
    }
    assertPrivilege("current_publication", "SELECT", true);
    assertPrivilege("current_publication", "UPDATE", true);
    assertPrivilege("current_publication", "INSERT", false);
    assertPrivilege("current_publication", "DELETE", false);
    assertThrows(
        DataAccessException.class,
        () -> jdbc.execute("delete from coordinator.current_publication"));
    assertEquals(
        1,
        jdbc.update(
            "update coordinator.current_publication set publication_id=publication_id where id=1"));
  }

  void assertPrivilege(String table, String permission, boolean expected) {
    assertEquals(
        expected,
        jdbc.queryForObject(
            "select has_table_privilege(current_user, ?, ?)",
            Boolean.class,
            "coordinator." + table,
            permission),
        table + ": " + permission);
  }

  @Test
  void runtimeCannotReadOwnersMigrationHistoryOrCreateTables() {
    for (String schema : List.of("records", "analysis")) {
      assertEquals(
          false,
          jdbc.queryForObject(
              "select has_schema_privilege(current_user, ?, 'USAGE')", Boolean.class, schema));
    }
    for (String table :
        List.of(
            "records.source_meeting",
            "analysis.meeting_analysis",
            "coordinator.flyway_schema_history")) {
      assertThrows(DataAccessException.class, () -> jdbc.queryForList("select * from " + table));
    }
    assertThrows(
        DataAccessException.class,
        () -> jdbc.execute("create table coordinator.forbidden(id integer)"));
    assertThrows(
        DataAccessException.class,
        () -> jdbc.execute("update coordinator.flyway_schema_history set success=false"));
  }

  @Test
  void constraintsRejectInvalidStatesCountersAndPartialFrozenRequests() {
    var r = run();
    var m = meeting(r);
    new TransactionTemplate(transactions)
        .executeWithoutResult(tx -> {
          entities.persist(r);
          entities.persist(m);
        });
    for (String change :
        List.of(
            "state='BAD'",
            "state='HALTED'",
            "state='ABANDONED'",
            "state='PUBLISHED'",
            "halt_kind='BAD'",
            "observed_state='BAD'",
            "discovery_attempt=0",
            "max_meetings=0",
            "command_sha256='bad'",
            "committee_ids='{}'")) {
      assertThrows(
          DataAccessException.class,
          () -> jdbc.update("update coordinator.run set " + change + " where id=?", r.id),
          change);
    }
    assertEquals(
        1,
        jdbc.update(
            "update coordinator.run set"
                + " state='HALTED',halt_kind='DISCOVER',halt_code='UNDATED_MEETING' where id=?",
            r.id));
    for (String change :
        List.of(
            "progress='BAD'",
            "progress='ANALYZED'",
            "progress='SKIPPED'",
            "progress='NO_INPUTS'",
            "no_input_reason='BAD'",
            "acquire_attempt=0",
            "prepare_attempt=0",
            "analyze_attempt=0",
            "frozen_request_json='{}'",
            "frozen_request_json='{}',frozen_request_sha256='bad'",
            "frozen_request_json='[]',frozen_request_sha256='" + "a".repeat(64) + "'")) {
      assertThrows(
          DataAccessException.class,
          () ->
              jdbc.update("update coordinator.run_meeting set " + change + " where run_id=?", r.id),
          change);
    }
    assertThrows(
        DataAccessException.class,
        () -> jdbc.execute("insert into coordinator.run_slot(id) values (2)"));
  }

  @Test
  void publicationDispositionRequiresRevisionOrSkipReason() {
    var r = run();
    var p = new PublicationEntity();
    p.runId = r.id;
    p.publishedAt = r.acceptedAt;
    new TransactionTemplate(transactions)
        .executeWithoutResult(tx -> {
          entities.persist(r);
          entities.flush();
          entities.persist(p);
        });
    for (String disposition : List.of("BAD", "ANALYZED", "SKIPPED")) {
      assertThrows(
          DataAccessException.class,
          () ->
              jdbc.update(
                  "insert into"
                      + " coordinator.publication_meeting(publication_id,meeting_id,official_id,meeting_date,title,disposition)"
                      + " values (?,?,'synthetic',?,'Synthetic',?)",
                  p.id,
                  UUID.randomUUID(),
                  r.rangeFrom,
                  disposition));
    }
  }

  @Test
  void meetingLimitHasNoUsableMissingOrNonpositiveValue() {
    assertThrows(IllegalArgumentException.class, () -> new CoordinatorSettings(0));
    assertThrows(IllegalArgumentException.class, () -> new CoordinatorSettings(-1));
    assertEquals(3, new CoordinatorSettings(3).maxMeetings());
  }
}
