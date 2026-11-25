package org.civictracker.coordinator.owners;

import java.util.UUID;

public record Prepared(
    UUID acquisitionItemId,
    UUID documentId,
    UUID versionId,
    UUID preparationId,
    String profileFingerprint) {}
