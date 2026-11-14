package org.civictracker.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.civictracker.records.application.RecordsFoundation;
import org.civictracker.records.domain.SourceInput;
import org.civictracker.records.persistence.Document;
import org.civictracker.records.persistence.Meeting;
import org.civictracker.records.persistence.MeetingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class RecordsFoundationTests {
  @Autowired RecordsFoundation records;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @Autowired MeetingRepository meetings;
  @Autowired PlatformTransactionManager manager;
  @Autowired Environment environment;

  @Test
  void sameSourceReusesIdentityAndChangesRetainVersionsIncludingReversion() throws Exception {
    Fixture f = new Fixture();
    var first = retain(f, "original");
    var repeat = retain(f, "original");
    assertEquals(first.documentId(), repeat.documentId());
    assertEquals(first.versionId(), repeat.versionId());
    f.next();
    var changed = retain(f, "revised!");
    assertEquals(first.documentId(), changed.documentId());
    assertNotEquals(first.versionId(), changed.versionId());
    assertEquals(2, changed.versionCount());
    try (var stream = records.openVersion(first.versionId())) {
      assertEquals("original", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }
    f.next();
    var reverted = retain(f, "original");
    assertEquals(first.versionId(), reverted.versionId());
    assertEquals(2, reverted.versionCount());
    assertEquals(4, observationCount(first.documentId()));
    assertEquals(4, first.documentId().version());
    assertEquals(4, first.versionId().version());
  }

  @Test
  void uniqueSameItemHashReusesIdentityAndRetainsSeveralHistoricalAliases() throws Exception {
    Fixture f = new Fixture();
    var first = retain(f, "bytes");
    f.id = "replacement";
    f.next();
    var second = retain(f, "bytes");
    assertEquals(first.documentId(), second.documentId());
    assertEquals(java.util.List.of("replacement"), second.primaryIds());
    assertEquals(java.util.List.of("doc"), second.historicalIds());
    f.id = "latest";
    f.next();
    var third = retain(f, "bytes");
    assertEquals(java.util.List.of("doc", "replacement"), third.historicalIds());
    f.id = "doc";
    f.current = false;
    f.next();
    assertEquals(java.util.List.of("latest"), retain(f, "bytes").primaryIds());
  }

  @Test
  void historicalAndConflictingObservationsCannotDemoteCurrentSelection() throws Exception {
    Fixture f = new Fixture();
    var first = retain(f, "first");
    f.id = "latest";
    f.next();
    retain(f, "first");
    f.id = "doc";
    var conflict = retain(f, "conflicting");
    assertEquals(first.versionId(), conflict.versionId());
    assertEquals(java.util.List.of("latest"), conflict.primaryIds());
    assertEquals(2, conflict.versionCount());
    f.at = f.at.minusSeconds(20);
    assertEquals(first.versionId(), retain(f, "older").versionId());
    assertEquals(4, observationCount(first.documentId()));
  }

  @Test
  void differentContextOrMissingScopeNeverAutomaticallyMergesEqualBytes() throws Exception {
    Fixture f = new Fixture();
    var first = retain(f, "same");
    f.id = "other";
    f.item = "other-item";
    assertNotEquals(first.documentId(), retain(f, "same").documentId());
    f.item = "item";
    f.meeting = "other-meeting";
    assertNotEquals(first.documentId(), retain(f, "same").documentId());
    f.meeting = "meeting";
    f.portal = "other-" + f.portal;
    assertNotEquals(first.documentId(), retain(f, "same").documentId());
  }

  @Test
  void ambiguousHashCandidatesRemainSeparateAndChangedNewIdDoesNotMerge() throws Exception {
    Fixture f = new Fixture();
    var a = retain(f, "a");
    f.id = "second";
    var b = retain(f, "b");
    assertNotEquals(a.documentId(), b.documentId());
    f.next();
    retain(f, "a");
    f.id = "third";
    f.next();
    var c = retain(f, "a");
    assertNotEquals(a.documentId(), c.documentId());
    assertNotEquals(b.documentId(), c.documentId());
  }

  @Test
  void meetingLevelHashRequiresTemplateOutputLanguageAndUniqueMatch() throws Exception {
    Fixture f = new Fixture();
    f.item = null;
    var first = retain(f, "packet");
    f.id = "new";
    f.next();
    assertEquals(first.documentId(), retain(f, "packet").documentId());
    f.id = "html";
    f.output = "HTML";
    assertNotEquals(first.documentId(), retain(f, "packet").documentId());
    f.id = "spanish";
    f.output = "PDF";
    f.language = "es";
    assertNotEquals(first.documentId(), retain(f, "packet").documentId());
    f.id = "missing";
    f.language = null;
    assertNotEquals(first.documentId(), retain(f, "packet").documentId());
    f.id = "template2";
    f.language = "en";
    f.template = "different";
    assertNotEquals(first.documentId(), retain(f, "packet").documentId());
  }

  @Test
  void exactUrlFallbackPreservesQueryAndContextWhileDetectingChangedBytes() throws Exception {
    Fixture f = new Fixture();
    f.id = null;
    var first = retain(f, "one");
    f.next();
    var changed = retain(f, "two");
    assertEquals(first.documentId(), changed.documentId());
    assertEquals(2, changed.versionCount());
    f.url += "&different=true";
    assertNotEquals(first.documentId(), retain(f, "two").documentId());
    f.url = "https://example.invalid/records?token=1";
    f.item = "other";
    assertNotEquals(first.documentId(), retain(f, "one").documentId());
  }

  @Test
  void urlFallbackDoesNotBorrowAUrlObservedUnderAnotherItem() throws Exception {
    Fixture f = new Fixture();
    var first = retain(f, "shared");
    f.item = "second";
    f.url = "https://example.invalid/second";
    f.next();
    assertEquals(first.documentId(), retain(f, "shared").documentId());
    f.id = null;
    f.url = "https://example.invalid/records?token=1";
    assertNotEquals(first.documentId(), retain(f, "shared").documentId());
  }

  @Test
  void suppliedRelationshipsAndMetadataChangesSurviveUnchangedBytes() throws Exception {
    Fixture f = new Fixture();
    var first = retain(f, "unchanged");
    f.item = "second-item";
    f.title = "Updated title";
    f.cancellation = "Official cancellation notice";
    f.next();
    assertEquals(first.versionId(), retain(f, "unchanged").versionId());
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(*) from records.document_context where document_id=?",
            Integer.class,
            first.documentId()));
    assertEquals(
        "Official cancellation notice",
        jdbc.queryForObject(
            "select cancellation_evidence from records.source_meeting where portal=?",
            String.class,
            f.portal));
    f.title = "Historical title";
    f.current = false;
    f.next();
    retain(f, "unchanged");
    assertEquals(
        "Updated title",
        jdbc.queryForObject(
            "select title from records.source_meeting where portal=?", String.class, f.portal));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from records.source_observation where document_id=? and"
                + " meeting_title=?",
            Integer.class,
            first.documentId(),
            "Historical title"));
  }

  @Test
  void explicitlyRetainedSharedContextIsNotRestrictedToOneParentMeeting() throws Exception {
    Fixture first = new Fixture();
    var a = retain(first, "shared original");
    Fixture second = new Fixture();
    second.portal = first.portal;
    second.meeting = "second-meeting";
    var b = retain(second, "separate original");
    assertNotEquals(a.documentId(), b.documentId());
    jdbc.update(
        "insert into records.document_context "
            + "select ?, ?, meeting_id,item_id,template_id,output_type,language "
            + "from records.document_context where document_id=?",
        UUID.randomUUID(),
        a.documentId(),
        b.documentId());
    second.id = "new-alias-in-shared-context";
    second.next();
    assertEquals(a.documentId(), retain(second, "shared original").documentId());
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(distinct meeting_id) from records.document_context where document_id=?",
            Integer.class,
            a.documentId()));
  }

  @Test
  void meetingLevelObservationKeepsItsOwnMeetingWhenContextsAreShared() throws Exception {
    Fixture first = new Fixture();
    first.item = null;
    var shared = retain(first, "shared packet");
    Fixture second = sharedContext(first, shared.documentId(), true);
    second.id = "second-meeting-packet";
    second.url = "https://example.invalid/second-meeting-packet";
    second.next();
    assertEquals(shared.documentId(), retain(second, "shared packet").documentId());
    UUID observedMeeting =
        jdbc.queryForObject(
            "select c.meeting_id from records.source_observation o "
                + "join records.document_context c on c.id=o.context_id where o.source_url=?",
            UUID.class,
            second.url);
    UUID expectedMeeting =
        jdbc.queryForObject(
            "select id from records.source_meeting where portal=? and official_id=?",
            UUID.class,
            second.portal,
            second.meeting);
    assertEquals(expectedMeeting, observedMeeting);
  }

  @Test
  void repeatedAliasValueInAnotherMeetingRetainsIdentityAfterByteChange() throws Exception {
    Fixture first = new Fixture();
    var shared = retain(first, "original");
    Fixture second = sharedContext(first, shared.documentId(), false);
    second.id = first.id;
    second.next();
    assertEquals(shared.documentId(), retain(second, "original").documentId());
    assertEquals(
        2,
        jdbc.queryForObject(
            "select count(distinct meeting_id) from records.source_alias "
                + "where document_id=? and family=? and value=?",
            Integer.class,
            shared.documentId(),
            "document-list",
            first.id));
    second.next();
    var changed = retain(second, "revised");
    assertEquals(shared.documentId(), changed.documentId());
    assertEquals(2, changed.versionCount());
    assertNotEquals(shared.versionId(), changed.versionId());
  }

  private Fixture sharedContext(Fixture first, UUID documentId, boolean meetingLevel)
      throws IOException {
    Fixture second = new Fixture();
    second.portal = first.portal;
    second.meeting = "second-meeting";
    second.id = "separate-document";
    second.item = meetingLevel ? null : first.item;
    var separate = retain(second, "separate bytes");
    jdbc.update(
        "insert into records.document_context "
            + "select ?, ?, meeting_id,item_id,template_id,output_type,language "
            + "from records.document_context where document_id=?",
        UUID.randomUUID(),
        documentId,
        separate.documentId());
    return second;
  }

  @Test
  @Timeout(30)
  void concurrentIdenticalImportsProduceOneIdentityVersionAndArtifact() throws Exception {
    SourceInput input = new Fixture().input();
    var tasks = new ArrayList<Callable<RecordsFoundation.Retained>>();
    for (int i = 0; i < 8; i++) {
      tasks.add(() -> records.retain(input, new ByteArrayInputStream(new byte[] {1, 2})));
    }
    try (var executor = Executors.newFixedThreadPool(4)) {
      var results = executor.invokeAll(tasks);
      var first = results.getFirst().get();
      for (var result : results) {
        assertEquals(first.documentId(), result.get().documentId());
        assertEquals(first.versionId(), result.get().versionId());
      }
      assertEquals(1, first.versionCount());
      assertEquals(8, observationCount(first.documentId()));
    }
  }

  @Test
  @Timeout(30)
  void concurrentReplacementIdsReconcileWithoutLosingNewestReference() throws Exception {
    Fixture fixture = new Fixture();
    var tasks = new ArrayList<Callable<RecordsFoundation.Retained>>();
    for (int i = 0; i < 8; i++) {
      fixture.id = "id-" + i;
      fixture.next();
      SourceInput input = fixture.input();
      tasks.add(() -> records.retain(input, new ByteArrayInputStream(new byte[] {7})));
    }
    try (var executor = Executors.newFixedThreadPool(4)) {
      var results = executor.invokeAll(tasks);
      UUID documentId = results.getFirst().get().documentId();
      for (var result : results) {
        assertEquals(documentId, result.get().documentId());
      }
      var finalState = records.inspect(documentId);
      assertEquals(java.util.List.of("id-7"), finalState.primaryIds());
      assertEquals(7, finalState.historicalIds().size());
      assertEquals(1, finalState.versionCount());
    }
  }

  @Test
  void assignedIdsRemainStableAcrossSaveLoadAndUpdate() {
    var tx = new TransactionTemplate(manager);
    UUID id =
        tx.execute(status -> {
          Meeting meeting = new Meeting();
          meeting.portal = UUID.randomUUID().toString();
          meeting.officialId = "id";
          UUID original = meeting.id;
          assertTrue(meeting.isNew());
          assertEquals(4, original.version());
          meetings.saveAndFlush(meeting);
          assertFalse(meeting.isNew());
          entities.clear();
          Meeting loaded = meetings.findById(original).orElseThrow();
          assertFalse(loaded.isNew());
          loaded.title = "updated";
          meetings.saveAndFlush(loaded);
          return loaded.id;
        });
    assertEquals("updated", meetings.findById(id).orElseThrow().title);
  }

  @Test
  void optimisticVersionRejectsStaleMutablePointerUpdate() throws Exception {
    var retained = retain(new Fixture(), "version");
    var tx = new TransactionTemplate(manager);
    Document stale =
        tx.execute(status -> {
          Document doc = entities.find(Document.class, retained.documentId());
          entities.detach(doc);
          return doc;
        });
    tx.executeWithoutResult(status -> {
      var doc = entities.find(Document.class, retained.documentId());
      doc.currentObservedAt = Instant.parse("2026-11-10T00:00:00Z");
    });
    stale.currentObservedAt = Instant.parse("2026-11-11T00:00:00Z");
    assertThrows(
        OptimisticLockException.class,
        () -> tx.executeWithoutResult(status -> entities.merge(stale)));
  }

  @Test
  void rollbackAfterInstallationLeavesOrphanButNoCommittedMetadata() throws Exception {
    Fixture f = new Fixture();
    var tx = new TransactionTemplate(manager);
    var retained =
        tx.execute(status -> {
          try {
            var result = retain(f, "orphan");
            status.setRollbackOnly();
            return result;
          } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
          }
        });
    assertThrows(IllegalArgumentException.class, () -> records.inspect(retained.documentId()));
    Path root = Path.of(environment.getRequiredProperty("records.storage-root"));
    assertTrue(Files.isRegularFile(root.resolve(retained.storageKey())));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from records.source_meeting where portal=?", Integer.class, f.portal));
  }

  @Test
  void runtimeRoleCannotRunDdlOrAccessOtherOwnerSchemas() {
    assertThrows(
        DataAccessException.class,
        () -> jdbc.execute("create table records.forbidden_runtime_ddl (id integer)"));
    assertThrows(
        DataAccessException.class,
        () -> jdbc.execute("update records.flyway_schema_history set success=false"));
    for (String schema : java.util.List.of("analysis", "coordinator")) {
      assertEquals(
          false,
          jdbc.queryForObject(
              "select has_schema_privilege(current_user, ?, 'USAGE')", Boolean.class, schema));
      assertThrows(
          DataAccessException.class,
          () -> jdbc.execute("create table " + schema + ".forbidden_records_table (id integer)"));
    }
  }

  @Test
  void databaseConstraintsRejectDuplicateAliasesAndCrossDocumentVersionPointers() throws Exception {
    var a = retain(new Fixture(), "a");
    var b = retain(new Fixture(), "b");
    assertThrows(
        DataAccessException.class,
        () ->
            jdbc.update(
                "update records.source_document set current_version_id=? where id=?",
                b.versionId(),
                a.documentId()));
    assertThrows(
        DataAccessException.class,
        () ->
            jdbc.update(
                "insert into records.source_alias select ?,document_id,meeting_id,family,value,"
                    + " false from records.source_alias where document_id=?",
                UUID.randomUUID(),
                a.documentId()));
  }

  @Test
  void migrationHistoryIsOwnedAndEachVersionRunsOnlyOnce() {
    org.springframework.jdbc.datasource.DriverManagerDataSource source =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            environment.getRequiredProperty("spring.flyway.url"),
            environment.getRequiredProperty("spring.flyway.user"),
            environment.getRequiredProperty("spring.flyway.password"));
    JdbcTemplate migrations = new JdbcTemplate(source);
    assertEquals(
        8,
        migrations.queryForObject(
            "select count(*) from records.flyway_schema_history where success and version is not"
                + " null",
            Integer.class));
    assertEquals(
        8,
        migrations.queryForObject(
            "select count(distinct version) from records.flyway_schema_history where success "
                + "and version is not null",
            Integer.class));
  }

  @Test
  void unknownDocumentAndVersionAreReportedAsMissing() {
    assertThrows(IllegalArgumentException.class, () -> records.inspect(UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class, () -> records.inspectVersion(UUID.randomUUID()));
  }

  @Test
  void observationPrecisionCannotPromoteAnEqualTimestampAfterDatabaseRoundTrip() throws Exception {
    Fixture f = new Fixture();
    f.at = Instant.parse("2026-11-08T12:00:00.123456789Z");
    var first = retain(f, "original");
    var conflict = retain(f, "conflicting");
    assertEquals(first.versionId(), conflict.versionId());
    assertEquals(2, conflict.versionCount());
  }

  @Test
  void invalidOrInterruptedInputDoesNotCreateMetadata() {
    Fixture f = new Fixture();
    assertThrows(
        IOException.class,
        () ->
            records.retain(
                f.input(),
                new java.io.InputStream() {
                  @Override
                  public int read() throws IOException {
                    throw new IOException("Interrupted");
                  }
                }));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from records.source_meeting where portal=?", Integer.class, f.portal));
    f.url = "../not-absolute";
    assertThrows(IllegalArgumentException.class, f::input);
  }

  @Test
  void fileInstallationFailureRollsBackMetadataAndCleansStaging(@TempDir Path root)
      throws Exception {
    var store =
        new org.civictracker.records.storage.OriginalFileStore(root) {
          @Override
          public String install(Staged staged, UUID artifactId) throws IOException {
            throw new IOException("Synthetic installation failure");
          }
        };
    var failing = new RecordsFoundation(entities, jdbc, manager, store);
    Fixture f = new Fixture();
    assertThrows(
        IOException.class,
        () -> failing.retain(f.input(), new ByteArrayInputStream(new byte[] {1})));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from records.source_meeting where portal=?", Integer.class, f.portal));
    try (var paths = Files.list(root.resolve("staging"))) {
      assertEquals(0, paths.count());
    }
  }

  private RecordsFoundation.Retained retain(Fixture fixture, String bytes) throws IOException {
    return records.retain(
        fixture.input(), new ByteArrayInputStream(bytes.getBytes(StandardCharsets.UTF_8)));
  }

  private int observationCount(UUID document) {
    return jdbc.queryForObject(
        "select count(*) from records.source_observation where document_id=?",
        Integer.class,
        document);
  }

  private static class Fixture {
    String portal = "test-" + UUID.randomUUID();
    String meeting = "meeting";
    String item = "item";
    String id = "doc";
    String template = "packet-template";
    String output = "PDF";
    String language = "en";
    String title = "Council meeting";
    String cancellation;
    String url = "https://example.invalid/records?token=1";
    Instant at = Instant.parse("2026-11-08T12:00:00Z");
    boolean current = true;

    void next() {
      at = at.plusSeconds(1);
    }

    SourceInput input() {
      return new SourceInput(
          portal,
          meeting,
          title,
          "2026-11-01",
          item,
          item,
          template,
          output,
          language,
          "document-list",
          id,
          URI.create(url),
          "../../original.pdf",
          at,
          current,
          cancellation);
    }
  }
}
