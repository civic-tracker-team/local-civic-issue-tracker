package org.civictracker.coordinator.run;

import java.time.Instant;
import java.util.UUID;

public record Halt(
    int haltId,
    StepKind kind,
    Integer ordinal,
    UUID operationId,
    String code,
    RetryHint hint,
    Instant at) {}
