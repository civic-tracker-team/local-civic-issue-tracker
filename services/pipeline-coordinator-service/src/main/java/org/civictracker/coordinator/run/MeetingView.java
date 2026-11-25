package org.civictracker.coordinator.run;

import java.time.LocalDate;
import java.util.UUID;

public record MeetingView(
    int ordinal,
    UUID meetingId,
    String officialId,
    LocalDate date,
    String title,
    String progress,
    UUID revisionId,
    String skipReason,
    Attempts attempts) {}
