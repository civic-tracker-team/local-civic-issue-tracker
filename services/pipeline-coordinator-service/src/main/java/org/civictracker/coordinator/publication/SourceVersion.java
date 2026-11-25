package org.civictracker.coordinator.publication;

import java.util.UUID;

public record SourceVersion(UUID documentId, UUID versionId) {}
