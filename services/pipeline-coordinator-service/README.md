# Pipeline coordinator service

Java package: `org.civictracker.coordinator`.

Coordinates input preparation, chronological whole-meeting analysis, bounded runs and recovery. Owns durable execution state. A failed meeting blocks later meetings and runs until resolved; interactive work remains separate from processing.

The POM and empty Maven source/resource/test directories are the initial scaffold. Spring Batch remains conditional on verification of non-overlap and recovery behavior. No scheduler, job implementation, queue or service contract is added here.
