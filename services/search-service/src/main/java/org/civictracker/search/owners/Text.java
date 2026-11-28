package org.civictracker.search.owners;

/** Untrusted source text, normalized for storage and safe snippet markers. */
public final class Text {
  private Text() {}

  /**
   * Removes control characters other than tab and line breaks. PostgreSQL text cannot hold NUL,
   * and U+0001/U+0002 are reserved as snippet match markers.
   */
  public static String clean(String value) {
    if (value == null) return null;
    var out = new StringBuilder(value.length());
    value.codePoints()
        .filter(c -> c == '\t' || c == '\n' || c == '\r' || !(c < 0x20 || c == 0x7f))
        .forEach(out::appendCodePoint);
    return out.toString();
  }
}
