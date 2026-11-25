package org.civictracker.coordinator.owners;

import java.time.LocalDate;
import java.util.UUID;

public record DiscoveredMeeting(
    UUID id, String officialId, LocalDate localDate, String title, String cancellationEvidence) {}
