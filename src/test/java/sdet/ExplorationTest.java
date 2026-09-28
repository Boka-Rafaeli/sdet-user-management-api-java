package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExplorationTest {
  @Test
  void allPhasesAllOperationsAndInformationalFindings() throws Exception {
    try (var runtime = DockerRuntime.start();
        var client = new ApiClient(Settings.load(Map.of()).withBaseUrl(runtime.baseUrl()))) {
      var root = Files.createTempDirectory("exploration");
      var explorer =
          new Exploration(
              new Generation(new Contract()), client, new Exploration.Limits(42, 2, 100, 3, 2000));
      var result = explorer.run(root, runtime.digest());
      assertTrue(result.complete());
      assertEquals("completed", result.stopReason());
      assertEquals(5, result.counters().size());
      for (var counters : result.counters().values())
        for (String phase : List.of("examples", "coverage", "fuzzing"))
          assertTrue(counters.get(phase) > 0, phase);
      assertFalse(result.findings().isEmpty());
      for (String line : Files.readAllLines(root.resolve("events.ndjson"))) {
        var event = Json.parse(line);
        assertEquals(1, event.path("schemaVersion").asInt());
        assertEquals(13, event.path("checks").size());
        assertFalse(line.contains(client.settings().token()));
        if (event.has("minimal"))
          assertFalse(
              explorer.replay(event.get("minimal"), event.path("sequence").asInt()).isEmpty());
      }
    }
  }

  @Test
  void engineFailuresBlockAndReportWritingFails() throws Exception {
    var g = new Generation(new Contract());
    try (var client =
        new ApiClient(
            Settings.load(Map.of()),
            r -> {
              throw new java.io.IOException("network");
            })) {
      var root = Files.createTempDirectory("engine-error");
      var explorer = new Exploration(g, client, Exploration.Limits.defaults());
      assertThrows(IllegalStateException.class, () -> explorer.run(root, "test"));
      var summary = Json.parse(Files.readString(root.resolve("summary.json")));
      assertEquals("engine-error", summary.at("/result/stopReason").asText());
      assertFalse(summary.at("/result/complete").asBoolean());
      assertThrows(
          Exception.class,
          () ->
              new Exploration(g, client, Exploration.Limits.defaults())
                  .run(Files.createTempFile("not-directory", ".tmp"), "test"));
    }
  }

  @Test
  void explicitBudgets() throws Exception {
    try (var runtime = DockerRuntime.start();
        var client = new ApiClient(Settings.load(Map.of()).withBaseUrl(runtime.baseUrl()))) {
      var result =
          new Exploration(
                  new Generation(new Contract()), client, new Exploration.Limits(1, 1, 1, 0, 100))
              .run(Files.createTempDirectory("budget"), runtime.digest());
      assertEquals("failure-budget", result.stopReason());
      assertTrue(result.complete());
      var limited =
          new Exploration(
                  new Generation(new Contract()), client, new Exploration.Limits(1, 1, 100, 0, 1))
              .run(Files.createTempDirectory("records"), runtime.digest());
      assertEquals("record-budget", limited.stopReason());
    }
  }
}
