package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ParityTest {
  @Test
  void exactOutcomesAndIndependentMismatchWitnesses() throws Exception {
    var source = Path.of("reports/sample");
    assertEquals(111, Parity.compare(source, source).path("matched").asInt());
    var copy = Files.createTempDirectory("parity");
    for (String scope : List.of("dev", "prod", "isolation")) {
      Files.createDirectories(copy.resolve(scope + "/deterministic"));
      Files.copy(
          source.resolve(scope + "/deterministic/summary.json"),
          copy.resolve(scope + "/deterministic/summary.json"));
    }
    var file = copy.resolve("dev/deterministic/summary.json");
    var original = Json.parse(Files.readString(file));
    var changed = original.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) changed.path("results").get(0))
        .put("status", "XFAIL");
    Files.writeString(file, Json.text(changed));
    assertThrows(IllegalStateException.class, () -> Parity.compare(source, copy));
    ((com.fasterxml.jackson.databind.node.ObjectNode) changed).put("imageDigest", "wrong");
    Files.writeString(file, Json.text(changed));
    assertThrows(IllegalArgumentException.class, () -> Parity.compare(source, copy));
    var duplicate = original.deepCopy();
    ((com.fasterxml.jackson.databind.node.ArrayNode) duplicate.path("results"))
        .add(duplicate.path("results").get(0));
    Files.writeString(file, Json.text(duplicate));
    assertThrows(IllegalArgumentException.class, () -> Parity.compare(source, copy));
  }
}
