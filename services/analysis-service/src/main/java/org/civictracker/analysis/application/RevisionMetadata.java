package org.civictracker.analysis.application;

import java.util.UUID;
import org.civictracker.analysis.persistence.AnalysisRevision.Origin;

/** Provenance supplied by the operation or verified trial importer. */
public record RevisionMetadata(
    Origin origin,
    UUID operationId,
    String contractRevision,
    String contractDetailJson,
    String profileFingerprint,
    String inputSha256,
    String modelInputSha256,
    String candidatesSha256,
    String receiptSha256,
    long promptTokens,
    String artifactKey) {}
