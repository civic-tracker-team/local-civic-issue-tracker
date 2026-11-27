package org.civictracker.search.web;

import java.util.ArrayList;
import java.util.List;
import org.civictracker.search.owners.Text;
import org.civictracker.search.query.SearchQueries;
import org.civictracker.search.query.SearchResponse;
import org.civictracker.search.query.SearchUnavailable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Public keyword search over the published collection, with selectable result types. */
@RestController
@RequestMapping("/api/v1")
public class SearchController {
  static final int MAX_QUERY_LENGTH = 200;
  static final int MAX_LIMIT = 50;
  static final int MAX_OFFSET = 1_000;
  private static final Logger LOG = LoggerFactory.getLogger(SearchController.class);

  private final SearchQueries queries;

  public SearchController(SearchQueries queries) {
    this.queries = queries;
  }

  /**
   * {@code types} is a comma-separated or repeated list of issues, records and meetings; all
   * three when omitted. {@code limit} and {@code offset} apply within each selected type.
   */
  @GetMapping("/search")
  public ResponseEntity<SearchResponse> search(
      @RequestParam("q") String q,
      @RequestParam(name = "types", required = false) List<String> types,
      @RequestParam(name = "limit", defaultValue = "10") int limit,
      @RequestParam(name = "offset", defaultValue = "0") int offset) {
    var query = q.strip();
    // Control characters can't reach PostgreSQL (it rejects NUL) and are never indexed.
    if (query.isEmpty()
        || query.codePointCount(0, query.length()) > MAX_QUERY_LENGTH
        || !query.equals(Text.clean(query))) {
      throw new BadSearch("INVALID_QUERY");
    }
    if (limit < 1 || limit > MAX_LIMIT || offset < 0 || offset > MAX_OFFSET) {
      throw new BadSearch("INVALID_PAGE");
    }
    var selected = new ArrayList<String>();
    for (var type : types == null ? SearchQueries.ORDER : types) {
      var name = type.strip();
      if (!SearchQueries.TYPES.containsKey(name)) throw new BadSearch("INVALID_TYPE");
      if (!selected.contains(name)) selected.add(name);
    }
    if (selected.isEmpty()) throw new BadSearch("INVALID_TYPE");
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(queries.search(query, selected, limit, offset));
  }

  static final class BadSearch extends RuntimeException {
    BadSearch(String code) {
      super(code);
    }
  }

  @ExceptionHandler(BadSearch.class)
  ResponseEntity<ProblemDetail> bad(BadSearch error) {
    return problem(400, error.getMessage());
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> malformed(Exception error) {
    return problem(400, "INVALID_REQUEST");
  }

  @ExceptionHandler(SearchUnavailable.class)
  ResponseEntity<ProblemDetail> unavailable(SearchUnavailable error) {
    return problem(503, "NO_PUBLISHED_INDEX");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ProblemDetail> database(DataAccessException error) {
    LOG.warn("Search database read failed: {}", error.getClass().getSimpleName());
    return problem(503, "SEARCH_UNAVAILABLE");
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<ProblemDetail> unexpected(RuntimeException error) {
    LOG.error("Unexpected search failure", error);
    return problem(500, "INTERNAL_ERROR");
  }

  private static ResponseEntity<ProblemDetail> problem(int status, String code) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
        .body(SearchProblems.problem(status, code));
  }
}
