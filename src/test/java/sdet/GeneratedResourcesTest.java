package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class GeneratedResourcesTest {
  @Test
  void verifiedSetupPreservesNegativeInputsAndTracksRenames() throws Exception {
    var requests = new ArrayList<ApiClient.Request>();
    try (var client =
        new ApiClient(
            Settings.load(Map.of()),
            r -> {
              requests.add(r);
              String body =
                  "{\"name\":\"Generated Resource\",\"email\":\"owned@example.com\",\"age\":42}";
              return ContractTest.response(
                  r.method().equals("DELETE") ? 204 : r.method().equals("POST") ? 201 : 200,
                  "application/json",
                  r.method().equals("DELETE") ? "" : body);
            })) {
      var g = new Generation(new Contract());
      var op =
          g.operations().stream().filter(o -> o.method().equals("PUT")).findFirst().orElseThrow();
      var negative =
          g.coverage(op, "token").stream()
              .filter(c -> c.category().equals("missing-name"))
              .findFirst()
              .orElseThrow();
      var resources = new GeneratedResources(client, "owned@example.com");
      var prepared = resources.prepare(negative);
      assertEquals(negative.body(), prepared.body());
      assertEquals("owned@example.com", prepared.parameters().get("email"));
      assertEquals(
          List.of("DELETE", "POST", "GET"),
          requests.stream().map(ApiClient.Request::method).toList());
      var invalidPath =
          g.coverage(op, "token").stream().filter(c -> !c.positivePath()).findFirst().orElseThrow();
      assertEquals(invalidPath, resources.prepare(invalidPath));
      assertEquals(3, requests.size());
      resources.track(Json.parse("{\"email\":\"renamed@example.com\"}"));
      resources.close();
      int count = requests.size();
      resources.close();
      assertEquals(count, requests.size());
      assertTrue(
          requests.stream().anyMatch(r -> r.uri().getRawPath().contains("renamed%40example.com")));
      assertThrows(IllegalStateException.class, resources::prepare);
    }
  }

  @Test
  void setupFailuresAreEngineErrors() throws Exception {
    for (String method : List.of("DELETE", "POST", "GET"))
      try (var client =
          new ApiClient(
              Settings.load(Map.of()),
              r ->
                  ContractTest.response(
                      r.method().equals(method)
                          ? 500
                          : r.method().equals("DELETE")
                              ? 204
                              : r.method().equals("POST") ? 201 : 200,
                      "application/json",
                      r.method().equals("DELETE")
                          ? ""
                          : "{\"name\":\"Generated Resource\",\"email\":\"a@b\",\"age\":42}"))) {
        var resources = new GeneratedResources(client, "a@b");
        assertThrows(java.io.IOException.class, resources::prepare);
        resources.close();
      }
  }

  @Test
  void bestEffortTransportCleanupContinues() {
    var seen = new ArrayList<String>();
    try (var client =
        new ApiClient(
            Settings.load(Map.of()),
            r -> {
              seen.add(r.uri().toString());
              throw new java.io.IOException("network");
            })) {
      var resources = new GeneratedResources(client, "a@b");
      resources.track(Json.parse("{\"email\":\"c@d\"}"));
      resources.close();
      assertEquals(2, seen.size());
    }
  }
}
