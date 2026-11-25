package org.civictracker.coordinator.run;

import java.time.LocalDate;
import java.util.UUID;

public record PlannedMeeting(
    int ordinal,
    UUID meetingId,
    String officialId,
    LocalDate date,
    String title,
    boolean cancellationEvidence) {}
