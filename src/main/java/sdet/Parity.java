package sdet;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;

/** Compare externally produced outcomes; never derive reference answers from Java results. */
public final class Parity {
  private Parity() {}

  private static Map<String, String> outcomes(JsonNode report, List<String> expected) {
    if (!report.path("imageDigest").asText().equals(DockerRuntime.IMAGE))
      throw new IllegalArgumentException("Parity image digest differs");
    var result = new TreeMap<String, String>();
    for (var row : report.path("results")) {
      String id = row.path("id").asText(), status = row.path("status").asText();
      if (!Set.of("PASS", "XFAIL").contains(status) || result.put(id, status) != null)
        throw new IllegalArgumentException("Invalid, duplicate or failing parity outcome");
    }
    if (!result.keySet().equals(new TreeSet<>(expected)))
      throw new IllegalArgumentException("Parity IDs differ from manifest");
    return result;
  }

  public static JsonNode compare(Path reference, Path actual) throws Exception {
    var rows = Json.MAPPER.createArrayNode();
    for (String scope : List.of("dev", "prod", "isolation")) {
      var expected = Verification.expected(scope);
      var left =
          outcomes(
              Json.parse(
                  Files.readString(reference.resolve(scope + "/deterministic/summary.json"))),
              expected);
      var right =
          outcomes(
              Json.parse(Files.readString(actual.resolve(scope + "/deterministic/summary.json"))),
              expected);
      if (!left.equals(right)) throw new IllegalStateException("Outcome mismatch in " + scope);
      for (String id : expected)
        rows.addObject()
            .put("scope", scope)
            .put("id", id)
            .put("typescript", left.get(id))
            .put("java", right.get(id));
    }
    var report =
        Json.MAPPER
            .createObjectNode()
            .put("schemaVersion", 1)
            .put("referenceCommit", "d21686120c38aa6292d6370dc0bfe09aeff039f1")
            .put("image", DockerRuntime.IMAGE)
            .put("matched", rows.size());
    report.set("results", rows);
    return report;
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 3)
      throw new IllegalArgumentException(
          "Usage: Parity <reference-report-root> <java-report-root> <output.json>");
    var result = compare(Path.of(args[0]), Path.of(args[1]));
    Files.writeString(
        Path.of(args[2]),
        Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n");
    System.out.println("Parity matched IDs: " + result.path("matched").asInt());
  }
}
