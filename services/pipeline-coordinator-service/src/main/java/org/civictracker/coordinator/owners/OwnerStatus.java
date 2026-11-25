package org.civictracker.coordinator.owners;

import java.net.URI;
import java.util.UUID;

public record OwnerStatus(
    UUID operationId,
    OwnerState state,
    String errorCode,
    UUID blockedId,
    UUID revisionId,
    URI statusUrl) {}
