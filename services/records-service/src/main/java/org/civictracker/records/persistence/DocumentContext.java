package org.civictracker.records.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "document_context", schema = "records")
public class DocumentContext extends AssignedEntity {
  public UUID documentId;
  public UUID meetingId;
  public UUID itemId;
  public String templateId;
  public String outputType;
  public String language;
}
