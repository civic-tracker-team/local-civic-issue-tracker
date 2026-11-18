package org.civictracker.records.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Lowercase hexadecimal SHA-256 digests for fingerprints and content identities. */
public final class Hashes {
  private Hashes() {}

  public static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }
}
