package org.civictracker.records.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {}
