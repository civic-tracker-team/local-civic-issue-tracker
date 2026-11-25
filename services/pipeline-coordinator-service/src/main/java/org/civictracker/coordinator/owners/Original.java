package org.civictracker.coordinator.owners;

import java.util.UUID;

public record Original(
    UUID acquisitionItemId,
    UUID documentId,
    UUID versionId,
    UUID artifactId,
    String sha256,
    long byteLength) {}
