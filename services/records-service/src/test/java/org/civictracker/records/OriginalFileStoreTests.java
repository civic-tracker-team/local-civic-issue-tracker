package org.civictracker.records;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.civictracker.records.storage.OriginalFileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OriginalFileStoreTests {
  @TempDir Path root;

  @Test
  void streamsExactBytesAndUsesRelativeKeysIndependentOfFilename() throws Exception {
    OriginalFileStore store = new OriginalFileStore(root);
    byte[] original = new byte[25001];
    new java.util.Random(7).nextBytes(original);
    String key;
    try (var staged = store.stage(new ByteArrayInputStream(original))) {
      assertEquals(original.length, staged.byteLength());
      assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(original)), staged.sha256());
      key = store.install(staged, UUID.randomUUID());
      assertFalse(Path.of(key).isAbsolute());
    }
    try (var stream = store.open(key)) {
      assertArrayEquals(original, stream.readAllBytes());
    }
    assertEquals(0, stagingCount());
  }

  @Test
  void equalLengthIsNotEqualContent() throws Exception {
    OriginalFileStore store = new OriginalFileStore(root);
    try (var a = store.stage(new ByteArrayInputStream(new byte[] {1, 2}));
        var b = store.stage(new ByteArrayInputStream(new byte[] {2, 1}))) {
      assertEquals(a.byteLength(), b.byteLength());
      assertNotEquals(a.sha256(), b.sha256());
    }
    assertEquals(0, stagingCount());
  }

  @Test
  void interruptedInputRemovesPartialFileWithoutRemovingRetainedOriginal() throws Exception {
    OriginalFileStore store = new OriginalFileStore(root);
    String key;
    try (var staged = store.stage(new ByteArrayInputStream(new byte[] {3}))) {
      key = store.install(staged, UUID.randomUUID());
    }
    InputStream broken = new InputStream() {
      int count;
      @Override
      public int read() throws IOException {
        if (++count > 2) {
          throw new IOException("Synthetic interrupted input");
        }
        return 1;
      }
    };
    assertThrows(IOException.class, () -> store.stage(broken));
    assertEquals(0, stagingCount());
    try (var stream = store.open(key)) { assertArrayEquals(new byte[] {3}, stream.readAllBytes()); }
  }

  @Test
  void rejectsEscapesAndAbsoluteReferences() throws Exception {
    OriginalFileStore store = new OriginalFileStore(root);
    for (String key : new String[] {"../outside", "originals/../../outside", ".",
        root.resolve("outside").toString()}) {
      assertThrows(IOException.class, () -> store.open(key));
    }
  }

  @Test
  void existingArtifactIsNeverReplacedOnAnInstallFailure() throws Exception {
    OriginalFileStore store = new OriginalFileStore(root);
    UUID id = UUID.randomUUID();
    String key;
    try (var staged = store.stage(new ByteArrayInputStream(new byte[] {1}))) {
      key = store.install(staged, id);
    }
    try (var staged = store.stage(new ByteArrayInputStream(new byte[] {2}))) {
      assertThrows(IOException.class, () -> store.install(staged, id));
    }
    try (var stream = store.open(key)) { assertArrayEquals(new byte[] {1}, stream.readAllBytes()); }
    assertEquals(0, stagingCount());
  }

  @Test
  void rootCanBeRelocatedWithoutChangingDatabaseKey() throws Exception {
    Path first = root.resolve("first");
    OriginalFileStore store = new OriginalFileStore(first);
    String key;
    try (var staged = store.stage(new ByteArrayInputStream(new byte[] {9}))) {
      key = store.install(staged, UUID.randomUUID());
    }
    Path second = root.resolve("second");
    Files.move(first, second);
    try (var stream = new OriginalFileStore(second).open(key)) {
      assertArrayEquals(new byte[] {9}, stream.readAllBytes());
    }
  }

  @Test
  void storageInitializationFailsWhenDirectoryCannotBeCreated() throws Exception {
    Path blocked = root.resolve("blocked");
    Files.writeString(blocked, "not a directory");
    assertThrows(IOException.class, () -> new OriginalFileStore(blocked.resolve("child")));
  }

  @Test
  void symbolicLinkOrWindowsJunctionCannotEscapeStorageRoot() throws Exception {
    Path storage = root.resolve("storage");
    OriginalFileStore store = new OriginalFileStore(storage);
    Path outside = Files.createDirectory(root.resolve("outside"));
    Files.writeString(outside.resolve("secret"), "outside storage");
    Path link = storage.resolve("originals/link");
    if (System.getProperty("os.name").startsWith("Windows")) {
      Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J",
          link.toString(), outside.toString()).redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes());
      assertEquals(0, process.waitFor(), output);
    } else {
      Files.createSymbolicLink(link, outside);
    }
    try {
      assertThrows(IOException.class, () -> store.open("originals/link/secret"));
    } finally {
      Files.delete(link);
    }
    assertEquals("outside storage", Files.readString(outside.resolve("secret")));
  }

  private long stagingCount() throws IOException {
    try (var paths = Files.list(root.resolve("staging"))) {
      return paths.count();
    }
  }
}
