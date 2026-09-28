package sdet;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class Reports {
  public record Entry(String id, String status, String bug, String detail) {}

  private Reports() {}

  public static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
        .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
  }

  public static boolean complete(List<String> expected, List<Entry> entries) {
    return !expected.isEmpty()
        && new HashSet<>(expected).size() == expected.size()
        && entries.size() == expected.size()
        && new HashSet<>(entries.stream().map(Entry::id).toList()).equals(new HashSet<>(expected))
        && entries.stream().allMatch(e -> Set.of("PASS", "XFAIL").contains(e.status()));
  }

  public static void write(
      Path dir, String scope, String digest, List<String> expected, List<Entry> entries)
      throws IOException {
    var redaction = new Redaction(System.getenv().getOrDefault("AUTH_TOKEN", "mysecrettoken"));
    entries =
        entries.stream()
            .map(
                e ->
                    new Entry(
                        redaction.text(e.id()), e.status(), e.bug(), redaction.body(e.detail())))
            .toList();
    Files.createDirectories(dir);
    boolean ok = complete(expected, entries);
    var counts = new TreeMap<String, Long>();
    for (String status : List.of("PASS", "XFAIL", "FAIL", "ABORTED", "SKIP"))
      counts.put(status, entries.stream().filter(e -> e.status().equals(status)).count());
    Files.writeString(
        dir.resolve("summary.json"),
        Json.text(
                Map.of(
                    "schemaVersion",
                    1,
                    "scope",
                    scope,
                    "imageDigest",
                    digest,
                    "complete",
                    ok,
                    "expectedIds",
                    expected,
                    "counts",
                    counts,
                    "results",
                    entries))
            + "\n");
    var html =
        new StringBuilder(
            "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><title>Java API report</title><style>body{font:16px system-ui;max-width:1200px;margin:2em auto;padding:1em}td,th{padding:.6em;border-bottom:1px solid #ccc;text-align:left}table{border-collapse:collapse;width:100%}pre{white-space:pre-wrap}</style><h1>Java API: "
                + escape(scope)
                + "</h1><p>Complete: "
                + ok
                + " · "
                + escape(counts.toString())
                + "</p><p>"
                + escape(digest)
                + "</p><table><tr><th>ID</th><th>Outcome</th><th>Evidence</th></tr>");
    var xml =
        new StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\""
                + escape(scope)
                + "\" tests=\""
                + entries.size()
                + "\" failures=\""
                + entries.stream()
                    .filter(e -> !Set.of("PASS", "XFAIL").contains(e.status()))
                    .count()
                + "\" skipped=\"0\">");
    for (var entry : entries) {
      html.append("<tr><td>")
          .append(escape(entry.id()))
          .append("</td><td>")
          .append(entry.status())
          .append("</td><td><pre>")
          .append(escape(entry.bug() + " " + entry.detail()))
          .append("</pre></td></tr>");
      xml.append("<testcase name=\"").append(escape(entry.id())).append("\">");
      if (entry.status().equals("XFAIL"))
        xml.append(
                "<properties><property name=\"outcome\" value=\"XFAIL\"/><property name=\"bug\" value=\"")
            .append(escape(entry.bug()))
            .append("\"/></properties>");
      else if (!entry.status().equals("PASS"))
        xml.append("<failure message=\"").append(escape(entry.detail())).append("\"/>");
      xml.append("</testcase>");
    }
    html.append("</table></html>");
    xml.append("</testsuite>");
    Files.writeString(dir.resolve("report.html"), html);
    Files.writeString(dir.resolve("junit.xml"), xml);
  }
}
