package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class BaselineTest {
  private final Settings settings = Settings.load(Map.of(), "--baseline");

  private void check(Baseline.Action expected, Baseline.Action signature) throws Exception {
    Baseline.check(settings, "BUG-001", Set.of("dev"), expected, signature);
  }

  @Test
  void exact() {
    assertThrows(Baseline.KnownDefect.class, () -> check(() -> fail("expected"), () -> {}));
  }

  @Test
  void changed() {
    assertThrows(AssertionError.class, () -> check(() -> fail("expected"), () -> fail("changed")));
  }

  @Test
  void fixed() {
    assertThrows(IllegalStateException.class, () -> check(() -> {}, () -> {}));
  }

  @Test
  void networkAndAsync() {
    assertThrows(
        IOException.class,
        () ->
            check(
                () -> {
                  throw new IOException("network");
                },
                () -> {}));
    assertThrows(
        java.util.concurrent.CompletionException.class,
        () ->
            check(() -> CompletableFuture.failedFuture(new IOException("async")).join(), () -> {}));
  }

  @Test
  void strictAndUnaffected() {
    assertThrows(
        AssertionError.class,
        () ->
            Baseline.check(Settings.load(Map.of()), "BUG", Set.of("dev"), () -> fail(), () -> {}));
    assertThrows(
        AssertionError.class,
        () ->
            Baseline.check(
                settings.withEnvironment("prod"), "BUG", Set.of("dev"), () -> fail(), () -> {}));
  }

  @Test
  void cleanupWins() {
    assertThrows(
        IOException.class,
        () ->
            Baseline.execute(
                () -> {
                  throw new Baseline.KnownDefect("BUG");
                },
                () -> {
                  throw new IOException("cleanup");
                }));
    assertThrows(
        IOException.class,
        () ->
            Baseline.execute(
                () -> {
                  throw new IOException("setup");
                },
                () -> {}));
  }
}
