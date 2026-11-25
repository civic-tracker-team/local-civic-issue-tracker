package org.civictracker.coordinator.run;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record Waiting(WaitingState state, UUID ownerBlockedId, URI ownerStatusUrl, Instant since) {}
