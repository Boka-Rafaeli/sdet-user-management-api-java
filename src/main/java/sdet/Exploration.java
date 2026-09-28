package sdet;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.jqwik.api.Shrinkable;

/** API findings are informational. Setup, transport, schema and evidence errors propagate. */
public final class Exploration {
  public record Limits(long seed, int examples, int failures, int shrink, int records) {
    public Limits {
      if (examples < 1 || examples > 10000 || failures < 1 || shrink < 0 || records < 1)
        throw new IllegalArgumentException("Invalid exploration limits");
    }

    public static Limits defaults() {
      return new Limits(424242, 20, 20, 30, 2000);
    }
  }

  public record Evidence(
      int schemaVersion,
      String generator,
      String operation,
      String phase,
      String category,
      long seed,
      Generation.Case request,
      ApiClient.Response response,
      List<GeneratedChecks.Check> checks,
      Generation.Case minimal,
      int shrinkAttempts) {}

  public record Result(
      boolean complete,
      String stopReason,
      int cases,
      Set<String> findings,
      Map<String, Map<String, Integer>> counters) {}

  private final Generation generation;
  private final ApiClient client;
  private final Limits limits;
  private final Redaction redact;
  private final List<com.fasterxml.jackson.databind.JsonNode> evidence = new ArrayList<>();
  private final Set<String> findings = new LinkedHashSet<>();
  private final Map<String, Map<String, Integer>> counters = new LinkedHashMap<>();
  private int count;

  public Exploration(Generation generation, ApiClient client, Limits limits) {
    this.generation = generation;
    this.client = client;
    this.limits = limits;
    redact = new Redaction(client.settings().token());
  }

  private record Checked(
      Generation.Case request, ApiClient.Response response, List<GeneratedChecks.Check> checks) {}

  private Checked execute(Generation.Operation op, Generation.Case input, int sequence)
      throws Exception {
    String resource =
        "sdet-generated-"
            + client.settings().environment()
            + "-"
            + limits.seed()
            + "-"
            + op.id().toLowerCase(Locale.ROOT)
            + "-"
            + sequence
            + "@example.com";
    try (var resources = new GeneratedResources(client, resource)) {
      var prepared = resources.prepare(input);
      // Reset POST keys in this isolated scope, including replayed keys, before the request.
      if (prepared.method().equals("POST")
          && prepared.body().isObject()
          && prepared.body().hasNonNull("email")
          && prepared.body().get("email").isTextual()
          && EmailFormats.generated(prepared.body().get("email").asText())) {
        String email = prepared.body().get("email").asText();
        var reset = client.delete(email);
        if (!Set.of(204, 404).contains(reset.status()))
          throw new java.io.IOException("POST resource reset failed");
        resources.track(prepared.body());
      }
      String path = prepared.path();
      for (var parameter : prepared.parameters().entrySet())
        path = path.replace("{" + parameter.getKey() + "}", ApiClient.encode(parameter.getValue()));
      if (path.contains("{")) throw new IllegalArgumentException("Unresolved generated path");
      var headers = new HashMap<>(prepared.headers());
      if (prepared.hasBody()) headers.put("Content-Type", "application/json");
      var response =
          client.raw(
              prepared.method(),
              path,
              prepared.hasBody()
                  ? Json.text(prepared.body()).getBytes(StandardCharsets.UTF_8)
                  : null,
              headers);
      return new Checked(
          prepared, response, GeneratedChecks.check(generation, op, prepared, response));
    }
  }

  private void runCase(
      Generation.Operation op, Generation.Case input, Shrinkable<Generation.Case> shrinkable)
      throws Exception {
    int sequence = count++;
    var checked = execute(op, input, sequence);
    counters.get(op.id()).merge(input.phase(), 1, Integer::sum);
    var failed =
        checked.checks().stream()
            .filter(c -> c.status().equals("FAIL"))
            .map(GeneratedChecks.Check::name)
            .toList();
    for (String name : failed) findings.add(op.id() + ":" + name);
    Generation.Case minimal = null;
    int attempts = 0;
    if (shrinkable != null && !failed.isEmpty()) {
      String target = failed.getFirst();
      var shrunk =
          Fuzzing.minimize(
              shrinkable,
              limits.shrink(),
              candidate ->
                  execute(op, candidate, sequence).checks().stream()
                      .anyMatch(c -> c.name().equals(target) && c.status().equals("FAIL")));
      minimal = shrunk.value();
      attempts = shrunk.attempts();
    }
    // Serialize raw bytes as UTF-8 text, never opaque base64 that bypasses redaction.
    var record = Json.MAPPER.createObjectNode();
    record
        .put("schemaVersion", 1)
        .put("generator", Fuzzing.VERSION)
        .put("operation", op.id())
        .put("phase", input.phase())
        .put("category", input.category())
        .put("seed", limits.seed())
        .put("sequence", sequence)
        .put("shrinkAttempts", attempts);
    record.set("request", Json.MAPPER.valueToTree(input));
    record.set("preparedRequest", Json.MAPPER.valueToTree(checked.request()));
    record.set("checks", Json.MAPPER.valueToTree(checked.checks()));
    if (minimal != null) record.set("minimal", Json.MAPPER.valueToTree(minimal));
    String safeResponse = redact.body(checked.response().text());
    record.put("responseTruncated", safeResponse.length() > 16384);
    record
        .putObject("response")
        .put("status", checked.response().status())
        .put("body", safeResponse.substring(0, Math.min(16384, safeResponse.length())))
        .set("headers", Json.MAPPER.valueToTree(checked.response().headers()));
    evidence.add(redact.json(record));
  }

  public Result run(Path output, String digest) throws Exception {
    net.jqwik.api.sessions.JqwikSession.start(Long.toString(limits.seed()));
    try {
      return runSession(output, digest);
    } finally {
      net.jqwik.api.sessions.JqwikSession.finish();
    }
  }

  private Result runSession(Path output, String digest) throws Exception {
    if (count != 0 || !evidence.isEmpty())
      throw new IllegalStateException("Explorer instances are single-use");
    var operations = generation.operations();
    for (var op : operations)
      counters.put(
          op.id(), new LinkedHashMap<>(Map.of("examples", 0, "coverage", 0, "fuzzing", 0)));
    String stop = "completed";
    Exception engine = null;
    try {
      outer:
      for (String phase : List.of("examples", "coverage", "fuzzing"))
        for (var op : operations) {
          var cases =
              phase.equals("fuzzing")
                  ? Fuzzing.cases(
                      generation,
                      op,
                      client.settings().token(),
                      limits.seed() + op.id().hashCode(),
                      limits.examples())
                  : List.<Shrinkable<Generation.Case>>of();
          var finite =
              phase.equals("examples")
                  ? List.of(generation.example(op, client.settings().token()))
                  : phase.equals("coverage")
                      ? generation.coverage(op, client.settings().token())
                      : List.<Generation.Case>of();
          for (var c : finite) {
            if (findings.size() >= limits.failures() || count >= limits.records()) {
              stop = findings.size() >= limits.failures() ? "failure-budget" : "record-budget";
              break outer;
            }
            if (Thread.currentThread().isInterrupted())
              throw new InterruptedException("Exploration cancelled");
            runCase(op, c, null);
          }
          for (var c : cases) {
            if (findings.size() >= limits.failures() || count >= limits.records()) {
              stop = findings.size() >= limits.failures() ? "failure-budget" : "record-budget";
              break outer;
            }
            if (Thread.currentThread().isInterrupted())
              throw new InterruptedException("Exploration cancelled");
            runCase(op, c.value(), c);
          }
        }
    } catch (Exception e) {
      stop = "engine-error";
      engine = e;
    }
    var result = new Result(engine == null, stop, count, Set.copyOf(findings), counters);
    write(output, digest, result, engine);
    if (engine != null)
      throw new IllegalStateException(
          "Generated engine failure: " + redact.text(engine.toString()));
    return result;
  }

  public List<GeneratedChecks.Check> replay(
      com.fasterxml.jackson.databind.JsonNode request, int sequence) throws Exception {
    var c = Fuzzing.replay(request, client.settings().token());
    var op =
        generation.operations().stream()
            .filter(o -> o.id().equals(c.operationId()))
            .findFirst()
            .orElseThrow();
    return execute(op, c, sequence).checks();
  }

  private void write(Path output, String digest, Result result, Exception engine) throws Exception {
    Files.createDirectories(output);
    var lines = new StringBuilder();
    for (var record : evidence) lines.append(Json.text(record)).append('\n');
    Files.writeString(output.resolve("events.ndjson"), lines);
    var summary =
        Json.MAPPER.valueToTree(
            Map.of(
                "schemaVersion",
                1,
                "generator",
                Fuzzing.VERSION,
                "imageDigest",
                digest,
                "limits",
                limits,
                "result",
                result,
                "engineError",
                engine == null ? "" : redact.text(engine.toString()),
                "catalogue",
                GeneratedChecks.NAMES));
    Files.writeString(output.resolve("summary.json"), Json.text(redact.json(summary)) + "\n");
    var xml =
        new StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"generated\" tests=\""
                + (evidence.size() + (engine == null ? 0 : 1))
                + "\" failures=\""
                + (result.findings().isEmpty() && engine == null
                    ? 0
                    : evidence.stream()
                            .filter(
                                e -> {
                                  for (var c : e.path("checks"))
                                    if (c.path("status").asText().equals("FAIL")) return true;
                                  return false;
                                })
                            .count()
                        + (engine == null ? 0 : 1))
                + "\">");
    for (var record : evidence) {
      xml.append("<testcase name=\"")
          .append(
              Reports.escape(
                  record.path("operation").asText()
                      + "/"
                      + record.path("phase").asText()
                      + "/"
                      + record.path("sequence").asInt()))
          .append("\">");
      var failures = new ArrayList<String>();
      for (var c : record.path("checks"))
        if (c.path("status").asText().equals("FAIL")) failures.add(c.path("name").asText());
      if (!failures.isEmpty())
        xml.append("<failure message=\"")
            .append(Reports.escape(String.join(",", failures)))
            .append("\"/>");
      xml.append("</testcase>");
    }
    if (engine != null)
      xml.append("<testcase name=\"engine\"><error message=\"engine failure\"/></testcase>");
    xml.append("</testsuite>");
    Files.writeString(output.resolve("junit.xml"), xml);
  }
}
