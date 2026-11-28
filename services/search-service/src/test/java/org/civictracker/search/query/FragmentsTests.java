package org.civictracker.search.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class FragmentsTests {
  @Test
  void splitsHeadlinesAtMatchMarkers() {
    assertEquals(
        List.of(
            new SearchResponse.Fragment("The ", false),
            new SearchResponse.Fragment("parking", true),
            new SearchResponse.Fragment(" <i>lot</i>", false)),
        SearchQueries.fragments("The \u0001parking\u0002 <i>lot</i>"));
    assertEquals(List.of(), SearchQueries.fragments(""));
    assertEquals(List.of(), SearchQueries.fragments(null));
  }
}
