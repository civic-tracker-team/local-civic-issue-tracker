package org.civictracker.analysis.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.FindingKind;
import org.civictracker.analysis.model.Interpretation;

/** Pins reply kinds to the exact ordered canonical developments. */
public record DevelopmentKinds(List<Entry> entries) {
  public DevelopmentKinds {
    entries = List.copyOf(entries);
  }

  public record Entry(Candidates.Development development, FindingKind kind) {
    public Entry {
      Objects.requireNonNull(development);
      Objects.requireNonNull(kind);
    }
  }

  public static DevelopmentKinds from(Interpretation.Interpreted interpreted) {
    var raw = interpreted.reply().path("developments");
    var canonical = interpreted.candidates().developments();
    if (!interpreted.accepted() || !raw.isArray() || raw.size() != canonical.size())
      throw mismatch();
    var entries = new ArrayList<Entry>();
    for (int i = 0; i < canonical.size(); i++) {
      var development = canonical.get(i);
      if (!development.id().equals("d" + (i + 1))) throw mismatch();
      FindingKind kind;
      try {
        kind = FindingKind.valueOf(raw.get(i).path("finding").path("kind").asText());
      } catch (IllegalArgumentException error) {
        throw mismatch();
      }
      entries.add(new Entry(development, kind));
    }
    return new DevelopmentKinds(entries);
  }

  public void requireMatches(Candidates candidates) {
    if (!entries.stream().map(Entry::development).toList().equals(candidates.developments()))
      throw mismatch();
  }

  private static SaveRejected mismatch() {
    return new SaveRejected(SaveRejected.Code.FINDING_KIND_MAPPING_MISMATCH);
  }
}
