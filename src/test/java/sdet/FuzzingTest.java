package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

class FuzzingTest {
  @org.junit.jupiter.api.BeforeEach
  void start() {
    net.jqwik.api.sessions.JqwikSession.start("42");
  }

  @org.junit.jupiter.api.AfterEach
  void finish() {
    net.jqwik.api.sessions.JqwikSession.finish();
  }

  @Test
  void seededFuzzContainsBothPositiveAndNegativePayloads() throws Exception {
    var generation = new Generation(new Contract());
    for (var operation : generation.operations())
      if (operation.body() != null) {
        var values =
            Fuzzing.cases(generation, operation, "token", 424242, 100).stream()
                .map(Shrinkable::value)
                .toList();
        assertTrue(values.stream().anyMatch(Generation.Case::positive));
        assertTrue(values.stream().anyMatch(c -> !c.positive()));
        for (var value : values) assertEquals(value.positive(), generation.valid(operation, value));
      }
  }

  @Test
  void seedAndSerializedReplay() throws Exception {
    var g = new Generation(new Contract());
    for (var op : g.operations()) {
      var a = Fuzzing.cases(g, op, "token", 42, 20);
      var b = Fuzzing.cases(g, op, "token", 42, 20);
      assertEquals(
          a.stream().map(Shrinkable::value).toList(), b.stream().map(Shrinkable::value).toList());
      for (var value : a) {
        var c = value.value();
        assertEquals(c.positive(), g.valid(op, c));
        var sanitized = new Redaction("token").json(Json.MAPPER.valueToTree(c));
        assertEquals(c, Fuzzing.replay(sanitized, "token"));
      }
    }
  }

  @Test
  void shrinkingRealJqwikCounterexampleAndOperationalErrors() throws Exception {
    var generator = Arbitraries.integers().between(0, 1000).generator(100);
    var random = new Random(424242);
    Shrinkable<Integer> original;
    do {
      original = generator.next(random);
    } while (original.value() < 10);
    var minimal = Fuzzing.minimize(original, 1000, n -> n >= 10);
    assertEquals(10, minimal.value());
    assertTrue(minimal.attempts() > 0);
    var witness = original;
    assertThrows(
        java.io.IOException.class,
        () ->
            Fuzzing.minimize(
                witness,
                10,
                n -> {
                  throw new java.io.IOException("transport");
                }));
    assertTrue(Fuzzing.minimize(witness, 0, n -> true).exhausted());
  }
}
