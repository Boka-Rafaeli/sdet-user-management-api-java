package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class DockerRuntimeTest {
  @Test
  void ownedCleanupAfterStartupFailure() {
    var calls = new ArrayList<List<String>>();
    assertThrows(
        IOException.class,
        () ->
            DockerRuntime.start(
                a -> {
                  calls.add(a);
                  if (a.getFirst().equals("run")) return "a".repeat(64);
                  if (a.getFirst().equals("inspect")) throw new IOException("failure");
                  return "";
                },
                false));
    assertEquals(List.of("rm", "--force", "a".repeat(64)), calls.getLast());
  }

  @Test
  void idempotentCleanup() throws Exception {
    var calls = new ArrayList<List<String>>();
    var r =
        DockerRuntime.start(
            a -> {
              calls.add(a);
              return switch (a.getFirst()) {
                case "run" -> "b".repeat(64);
                case "inspect" -> "12345";
                case "image" -> DockerRuntime.IMAGE;
                default -> "";
              };
            },
            false);
    assertEquals(URI.create("http://127.0.0.1:12345"), r.baseUrl());
    r.close();
    r.close();
    assertEquals(1, calls.stream().filter(a -> a.getFirst().equals("rm")).count());
  }

  @Test
  void boundedReadiness() {
    long start = System.nanoTime();
    assertThrows(
        IOException.class,
        () -> DockerRuntime.waitReady(URI.create("http://127.0.0.1:1"), Duration.ofMillis(100)));
    assertTrue(System.nanoTime() - start < 2_000_000_000L);
  }

  @Test
  void realIsolatedContainer() throws Exception {
    try (var runtime = DockerRuntime.start();
        var client = new ApiClient(Settings.load(Map.of()).withBaseUrl(runtime.baseUrl()))) {
      assertEquals(200, client.list().status());
      assertTrue(runtime.digest().contains("sha256:c80c42"));
    }
  }
}
