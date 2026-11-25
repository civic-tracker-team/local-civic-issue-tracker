package org.civictracker.coordinator.run;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record CurrentStep(
    StepKind kind,
    Integer ordinal,
    UUID operationId,
    URI ownerStatusUrl,
    String observedState,
    UUID ownerBlockedId,
    Instant observedAt) {}
