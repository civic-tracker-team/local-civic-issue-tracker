package org.civictracker.records.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OriginalFileStore {
  private final Path root;

  public OriginalFileStore(@Value("${records.storage-root}") Path configuredRoot)
      throws IOException {
    Files.createDirectories(configuredRoot);
    root = configuredRoot.toRealPath();
    Files.createDirectories(root.resolve("staging"));
    Files.createDirectories(root.resolve("originals"));
    Files.createDirectories(root.resolve("captures"));
    Files.createDirectories(root.resolve("prepared"));
    safe("staging");
    safe("originals");
  }

  public record Staged(Path path, String sha256, long byteLength) implements AutoCloseable {
    @Override
    public void close() throws IOException {
      Files.deleteIfExists(path);
    }
  }

  public Staged stage(InputStream input) throws IOException {
    Path file = Files.createTempFile(safe("staging"), "incoming-", ".part");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      long length = 0;
      try (FileChannel channel =
              FileChannel.open(file, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
          OutputStream output = Channels.newOutputStream(channel)) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
          output.write(buffer, 0, count);
          digest.update(buffer, 0, count);
          length += count;
        }
        channel.force(true);
      }
      return new Staged(file, HexFormat.of().formatHex(digest.digest()), length);
    } catch (IOException | RuntimeException | NoSuchAlgorithmException error) {
      Files.deleteIfExists(file);
      if (error instanceof IOException io) {
        throw io;
      }
      throw new IOException("Unable to stage original bytes", error);
    }
  }

  public String install(Staged staged, UUID artifactId) throws IOException {
    return installAt(staged, "originals/" + artifactId);
  }

  public String installCapture(Staged staged, UUID captureId) throws IOException {
    return installAt(staged, "captures/" + captureId);
  }

  public String installPrepared(Staged staged, UUID id) throws IOException {
    return installAt(staged, "prepared/" + id);
  }

  private String installAt(Staged staged, String key) throws IOException {
    if (!staged.path().normalize().getParent().equals(safe("staging"))
        || !Files.isRegularFile(staged.path(), LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Original was not staged in the configured store");
    }
    Path destination = safe(key);
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      throw new FileAlreadyExistsException(key);
    }
    Files.move(staged.path(), destination, StandardCopyOption.ATOMIC_MOVE);
    return key;
  }

  public InputStream open(String key) throws IOException {
    return Files.newInputStream(safe(key), LinkOption.NOFOLLOW_LINKS);
  }

  /** Checks the retained length before an HTTP response begins. */
  public InputStream openVerifiedLength(String key, long length) throws IOException {
    FileChannel channel =
        FileChannel.open(safe(key), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    try {
      if (channel.size() != length) {
        throw new IOException("Retained artifact length differs from its metadata");
      }
      return Channels.newInputStream(channel);
    } catch (IOException | RuntimeException error) {
      channel.close();
      throw error;
    }
  }

  private Path safe(String key) throws IOException {
    Path relative = Path.of(key);
    Path result = root.resolve(relative).normalize();
    if (relative.isAbsolute() || !result.startsWith(root) || result.equals(root)) {
      throw new IOException("Invalid storage reference");
    }
    Path cursor = root;
    for (Path part : root.relativize(result)) {
      cursor = cursor.resolve(part);
      if (Files.isSymbolicLink(cursor)) {
        throw new IOException("Symbolic storage reference");
      }
      if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
          && !cursor.toRealPath().startsWith(root)) {
        throw new IOException("Storage reference leaves configured root");
      }
    }
    return result;
  }
}
