package org.civictracker.analysis.model;

import java.util.List;
import org.civictracker.analysis.domain.Candidates;
import org.civictracker.analysis.domain.CitationIndex;
import org.civictracker.analysis.domain.Problem;
import tools.jackson.databind.JsonNode;

/** Separates malformed replies, unresolved sources and interpreted candidates. */
public sealed interface Interpretation {
  record Trace(
      String at,
      String unit,
      int firstLine,
      int lastLine,
      List<CitationIndex.Span> spans,
      Problem.Code problem) {
    public Trace {
      spans = List.copyOf(spans);
    }
  }

  record Malformed(List<Problem> problems) implements Interpretation {
    public Malformed {
      problems = List.copyOf(problems);
    }
  }

  record Unresolved(JsonNode reply, List<Trace> traces, List<Problem> problems)
      implements Interpretation {
    public Unresolved {
      traces = List.copyOf(traces);
      problems = List.copyOf(problems);
    }
  }

  record Interpreted(
      JsonNode reply, List<Trace> traces, Candidates candidates, List<Problem> problems)
      implements Interpretation {
    public Interpreted {
      traces = List.copyOf(traces);
      problems = List.copyOf(problems);
    }

    public boolean accepted() {
      return problems.stream().noneMatch(p -> p.code().invalidates());
    }
  }
}
