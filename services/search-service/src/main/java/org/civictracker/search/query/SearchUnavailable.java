package org.civictracker.search.query;

/** No published collection has been indexed yet. */
public final class SearchUnavailable extends RuntimeException {
  public SearchUnavailable() {
    super("NO_PUBLISHED_INDEX");
  }
}
