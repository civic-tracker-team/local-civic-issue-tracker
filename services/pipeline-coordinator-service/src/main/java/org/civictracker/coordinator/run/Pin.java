package org.civictracker.coordinator.run;

import java.util.UUID;

public record Pin(
    UUID acquisitionItemId,
    UUID documentId,
    UUID versionId,
    UUID artifactId,
    String sha256,
    long byteLength,
    UUID preparationId,
    String profileFingerprint) {}
