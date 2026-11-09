# Pipeline coordinator service

Java package: `org.civictracker.coordinator`.

Coordinates input preparation, chronological whole-meeting analysis, bounded runs and recovery. Owns durable execution state. A failed meeting blocks later meetings and runs until resolved; interactive work remains separate from processing.

The Spring Boot entry point starts the HTTP baseline, with a real-server startup test. Spring Batch remains conditional on verification of non-overlap and recovery behavior. No scheduler, durable job implementation, queue or processing contract is added here.
