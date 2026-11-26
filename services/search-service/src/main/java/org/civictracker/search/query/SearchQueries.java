package org.civictracker.search.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.civictracker.search.index.IndexPlan.Kind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** PostgreSQL full-text search over the current index, read in one consistent snapshot. */
@Service
public class SearchQueries {
  /** Selected types in response order, with their wire names. */
  public static final Map<String, Kind> TYPES =
      Map.of("issues", Kind.ISSUE, "records", Kind.RECORD, "meetings", Kind.MEETING);

  public static final List<String> ORDER = List.of("issues", "records", "meetings");
  static final char START = '\u0001';
  static final char STOP = '\u0002';
  private static final String HEADLINE =
      "StartSel=\"" + START + "\", StopSel=\"" + STOP + "\", MaxWords=35, MinWords=15,"
          + " MaxFragments=2, FragmentDelimiter=\" … \"";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final String MATCH =
      " FROM search.entry e, q WHERE e.build_id = ? AND e.kind = ? AND e.document @@ q.query";
  private static final String QUERY =
      "WITH q AS (SELECT websearch_to_tsquery('english', ?) AS query)";

  private final JdbcTemplate jdbc;

  public SearchQueries(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SearchResponse search(String q, List<String> types, int limit, int offset) {
    var builds =
        jdbc.query(
            "select b.id, b.publication_id, b.coverage_json, b.record_types_json,"
                + " b.meeting_count, b.untexted_versions, b.built_at from search.current_index c"
                + " join search.index_build b on b.id = c.build_id where c.id = 1",
            (rs, row) -> collection(rs));
    if (builds.isEmpty()) {
      throw new SearchUnavailable();
    }
    var build = builds.getFirst();
    var selected = ORDER.stream().filter(types::contains).toList();
    var totals = new LinkedHashMap<String, Long>();
    var lists = new LinkedHashMap<String, List<SearchResponse.Hit>>();
    for (var type : selected) {
      var kind = TYPES.get(type).name();
      totals.put(
          type,
          jdbc.queryForObject(
              QUERY
                  + " SELECT count(*) FROM (SELECT DISTINCT e.target_id, e.version_id"
                  + MATCH
                  + ") matched",
              Long.class,
              q,
              build.id(),
              kind));
      lists.put(
          type,
          jdbc.query(
              QUERY
                  + ", best AS (SELECT DISTINCT ON (e.target_id, e.version_id) e.target_id,"
                  + " e.version_id, e.label, e.context, e.body, e.location, e.details_json,"
                  + " e.sort_date, ts_rank(e.document, q.query, 1) AS rank"
                  + MATCH
                  + " ORDER BY e.target_id, e.version_id, ts_rank(e.document, q.query, 1) DESC,"
                  + " e.part), page AS (SELECT * FROM best ORDER BY rank DESC,"
                  + " sort_date DESC NULLS LAST, label, target_id, version_id LIMIT ? OFFSET ?)"
                  + " SELECT page.*, ts_headline('english', CASE WHEN page.body = '' THEN"
                  + " page.context ELSE page.body END, q.query, ?) AS snippet FROM page, q"
                  + " ORDER BY page.rank DESC, page.sort_date DESC NULLS LAST, page.label,"
                  + " page.target_id, page.version_id",
              (rs, row) -> hit(rs),
              q,
              build.id(),
              kind,
              limit,
              offset,
              HEADLINE));
    }
    return new SearchResponse(
        build.collection(),
        new SearchResponse.Query(q, selected, limit, offset),
        totals,
        lists.get("issues"),
        lists.get("records"),
        lists.get("meetings"));
  }

  private record Build(UUID id, SearchResponse.Collection collection) {}

  private static Build collection(ResultSet rs) throws SQLException {
    List<SearchResponse.Range> coverage =
        JSON.readValue(rs.getString("coverage_json"), new TypeReference<>() {});
    List<String> types =
        JSON.readValue(rs.getString("record_types_json"), new TypeReference<>() {});
    Timestamp built = rs.getTimestamp("built_at");
    return new Build(
        rs.getObject("id", UUID.class),
        new SearchResponse.Collection(
            rs.getObject("publication_id", UUID.class),
            built.toInstant(),
            coverage,
            types,
            rs.getInt("meeting_count"),
            rs.getInt("untexted_versions")));
  }

  private static SearchResponse.Hit hit(ResultSet rs) throws SQLException {
    Map<String, Object> details =
        JSON.readValue(rs.getString("details_json"), new TypeReference<>() {});
    var date = rs.getObject("sort_date", java.time.LocalDate.class);
    return new SearchResponse.Hit(
        rs.getObject("target_id", UUID.class),
        rs.getObject("version_id", UUID.class),
        rs.getString("label"),
        date,
        rs.getString("location"),
        fragments(rs.getString("snippet")),
        details);
  }

  /** Splits ts_headline output at the match markers. Source text is never treated as markup. */
  static List<SearchResponse.Fragment> fragments(String headline) {
    var result = new ArrayList<SearchResponse.Fragment>();
    if (headline == null || headline.isEmpty()) return result;
    var text = new StringBuilder();
    boolean match = false;
    for (int i = 0; i < headline.length(); i++) {
      char c = headline.charAt(i);
      if (c == START || c == STOP) {
        if (!text.isEmpty()) result.add(new SearchResponse.Fragment(text.toString(), match));
        text.setLength(0);
        match = c == START;
      } else {
        text.append(c);
      }
    }
    if (!text.isEmpty()) result.add(new SearchResponse.Fragment(text.toString(), match));
    return result;
  }
}
