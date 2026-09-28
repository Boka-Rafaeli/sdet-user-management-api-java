package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class GenerationTest {
  @Test
  void discoversFiveOperationsAndIndependentModes() throws Exception {
    var g = new Generation(new Contract());
    assertEquals(5, g.operations().size());
    int negative = 0;
    for (var op : g.operations()) {
      assertTrue(g.valid(op, g.example(op, "token")));
      var coverage = g.coverage(op, "token");
      for (var c : coverage) {
        assertEquals(c.positive(), g.valid(op, c), c.category());
        if (!c.positive()) negative++;
      }
    }
    assertTrue(negative > 50);
  }

  @Test
  void unsupportedSchemasFailBeforeRequests() throws Exception {
    for (String keyword : List.of("pattern", "allOf", "nullable", "oneOf", "not", "default")) {
      var document = new Contract().document();
      ((com.fasterxml.jackson.databind.node.ObjectNode) document.at("/components/schemas/User"))
          .put(keyword, "x");
      assertThrows(
          IllegalArgumentException.class,
          () -> new Generation(new Contract(document)).operations());
    }
    var document = new Contract().document();
    ((com.fasterxml.jackson.databind.node.ObjectNode) document.at("/paths/~1users/get"))
        .putObject("security");
    assertThrows(
        IllegalArgumentException.class, () -> new Generation(new Contract(document)).operations());
  }

  @Test
  void everyActiveDetectorHasIndependentPassAndFailWitness() throws Exception {
    var g = new Generation(new Contract());
    var witnessed = new HashMap<String, Set<String>>();
    for (var op : g.operations()) {
      var cases = new ArrayList<>(g.coverage(op, "token"));
      cases.add(g.example(op, "token"));
      for (var c : cases)
        for (int status : List.of(200, 201, 204, 302, 400, 401, 404, 405, 409, 500))
          for (String media : List.of("application/json", "text/plain"))
            for (String body :
                List.of(
                    "{}",
                    "[]",
                    "{\"error\":\"error\"}",
                    "{\"name\":\"N\",\"email\":\"a@b\",\"age\":42}",
                    "invalid")) {
              var r =
                  new ApiClient.Response(
                      status,
                      Map.of(
                          "content-type",
                          media,
                          "allow",
                          status == 405 || status == 200 ? String.join(",", op.methods()) : "BORK"),
                      body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                      null);
              for (var check : GeneratedChecks.check(g, op, c, r))
                witnessed.computeIfAbsent(check.name(), k -> new HashSet<>()).add(check.status());
            }
    }
    for (String name : GeneratedChecks.NAMES)
      if (Set.of(
              "response_headers_conformance",
              "use_after_free",
              "ensure_resource_availability",
              "ignored_auth")
          .contains(name)) assertEquals(Set.of("SKIP"), witnessed.get(name));
      else {
        assertTrue(witnessed.get(name).contains("PASS"), name);
        assertTrue(witnessed.get(name).contains("FAIL"), name);
      }
  }
}
