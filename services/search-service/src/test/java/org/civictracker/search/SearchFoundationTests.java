package org.civictracker.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** The runtime role reads and writes index rows only; migrations stay with the migration role. */
@SpringBootTest
class SearchFoundationTests {
  @Autowired JdbcTemplate jdbc;

  @Test
  void runtimeCannotAccessOtherSchemasHistoryOrDdl() {
    for (var schema : new String[] {"records", "analysis", "coordinator"}) {
      assertEquals(
          false,
          jdbc.queryForObject(
              "select has_schema_privilege(current_user, ?, 'USAGE')", Boolean.class, schema));
    }
    assertThrows(
        DataAccessException.class,
        () -> jdbc.queryForList("select * from search.flyway_schema_history"));
    assertThrows(
        DataAccessException.class,
        () -> jdbc.execute("create table search.forbidden (id integer)"));
    assertThrows(
        DataAccessException.class, () -> jdbc.execute("alter table search.entry drop column body"));
  }

  @Test
  void theCurrentPointerIsASingleRow() {
    assertThrows(
        DataAccessException.class, () -> jdbc.update("insert into search.current_index(id) values (2)"));
    assertEquals(1, jdbc.queryForObject("select count(*) from search.current_index", Integer.class));
  }
}
