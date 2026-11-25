package org.civictracker.coordinator.run;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public sealed interface Progress {
  record Discovered() implements Progress {}

  record Acquired() implements Progress {}

  record Prepared(List<Pin> pins) implements Progress {
    public Prepared {
      pins = List.copyOf(pins);
    }
  }

  record Analyzed(List<Pin> pins, UUID revisionId) implements Progress {
    public Analyzed {
      pins = List.copyOf(pins);
      Objects.requireNonNull(revisionId);
    }
  }

  record NoInputs(NoInputReason reason) implements Progress {
    public NoInputs {
      Objects.requireNonNull(reason);
    }
  }

  record Skipped(String reason, String maintainer, Instant at, List<Pin> pins) implements Progress {
    public Skipped {
      pins = List.copyOf(pins);
    }
  }
}
