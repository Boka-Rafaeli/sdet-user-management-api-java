package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AdvancedInfrastructureTest {
  @Test
  void rawBodiesDistinctAuthenticationAndClosedTransport() throws Exception {
    var requests = new ArrayList<ApiClient.Request>();
    var client =
        new ApiClient(
            Settings.load(Map.of()),
            r -> {
              requests.add(r);
              return ContractTest.response(200, "application/json", "{}");
            });
    for (String body : List.of("null", "[]", "\"text\"", "true", "42", "{}"))
      client.create(Json.parse(body));
    client.delete("a+%/@b");
    client.delete("a@b", null);
    client.delete("a@b", "");
    client.delete("a@b", "wrong");
    client.list();
    assertEquals(
        "null", new String(requests.getFirst().body(), java.nio.charset.StandardCharsets.UTF_8));
    assertEquals("mysecrettoken", requests.get(6).headers().get("authentication"));
    assertFalse(requests.get(7).headers().containsKey("authentication"));
    assertEquals("", requests.get(8).headers().get("authentication"));
    assertEquals("wrong", requests.get(9).headers().get("authentication"));
    assertNull(requests.get(10).body());
    assertFalse(requests.get(10).headers().containsKey("content-type"));
    client.close();
    assertThrows(IllegalStateException.class, client::list);
  }

  @Test
  void schemaSourceAndEmptyResponsesAndNumbers() throws Exception {
    var c = new Contract();
    var document = c.document();
    assertEquals("/dev", document.at("/servers/0/url").asText());
    assertEquals("/prod", document.at("/servers/1/url").asText());
    var body = Json.parse("{\"name\":\"\",\"email\":\"@\",\"age\":1,\"extra\":true}");
    assertTrue(c.valid(document.at("/components/schemas/User"), body));
    assertTrue(
        c.response(ContractTest.response(204, "", ""), "/users/{email}", "delete", 204).isNull());
    assertTrue(
        c.valid(
            document.at("/components/schemas/User"),
            Json.parse("{\"name\":\"N\",\"email\":\"a@b\",\"age\":150}")));
    assertThrows(IllegalArgumentException.class, () -> Json.parse(""));
    assertThrows(IllegalArgumentException.class, () -> Json.parse("{} {}"));
  }

  @Test
  void changedSchemaChangesGenerationAndInvalidExamplesFail() throws Exception {
    var d = new Contract().document();
    var properties =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            d.at("/components/schemas/CreateUserRequest/properties");
    ((com.fasterxml.jackson.databind.node.ObjectNode) properties.get("age"))
        .put("minimum", 10)
        .put("maximum", 20)
        .put("example", 15);
    properties.putObject("enabled").put("type", "boolean");
    ((com.fasterxml.jackson.databind.node.ArrayNode)
            d.at("/components/schemas/CreateUserRequest/required"))
        .add("enabled");
    var g = new Generation(new Contract(d));
    var op =
        g.operations().stream().filter(o -> o.method().equals("POST")).findFirst().orElseThrow();
    assertEquals(15, g.example(op, "token").body().path("age").asInt());
    assertTrue(
        g.coverage(op, "token").stream().anyMatch(c -> c.category().equals("missing-enabled")));
    assertTrue(g.coverage(op, "token").stream().anyMatch(c -> c.body().path("age").asInt() == 21));
    ((com.fasterxml.jackson.databind.node.ObjectNode) properties.get("age")).put("example", 99);
    var bad = new Generation(new Contract(d));
    var invalid =
        bad.operations().stream().filter(o -> o.method().equals("POST")).findFirst().orElseThrow();
    assertThrows(IllegalArgumentException.class, () -> bad.example(invalid, "token"));
  }

  @Test
  void redactionCyclesSharedNodesOverlapsAndMalformedUnicode() {
    var r = new Redaction("long-secret", "secret", "a b+%", "\ud800");
    assertEquals("<redacted>", r.text("long-secret"));
    assertEquals("<redacted>", r.text(ApiClient.encode("a b+%").replace("%20", "+")));
    assertEquals("<redacted>", r.text("\ud800"));
    var shared = Json.object("{\"__proto__\":\"safe\",\"password\":\"hidden\"}");
    var root = Json.MAPPER.createObjectNode();
    root.set("first", shared);
    root.set("second", shared);
    assertEquals(r.json(root).get("first"), r.json(root).get("second"));
    root.set("self", root);
    assertEquals("[Circular]", r.json(root).path("self").asText());
    assertFalse(r.clean(root));
  }

  @Test
  void readinessRetriesStatusThenSuccess() throws Exception {
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    server.createContext(
        "/",
        e -> {
          e.sendResponseHeaders(calls.incrementAndGet() < 3 ? 503 : 200, -1);
          e.close();
        });
    server.start();
    try {
      DockerRuntime.waitReady(
          java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
          java.time.Duration.ofSeconds(3));
      assertEquals(3, calls.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void atomicModesCrLfCollisionAndLaterBadFile() throws Exception {
    var root = Files.createTempDirectory("atomic-modes");
    var file = root.resolve("a.ndjson");
    Files.writeString(file, "{\"token\":\"secret\"}\r\n{}");
    var mode = java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----");
    Files.setPosixFilePermissions(file, mode);
    Evidence.scrub(root, new Redaction("secret"));
    assertEquals(mode, Files.getPosixFilePermissions(file));
    assertEquals(1, Evidence.verify(root, new Redaction("secret")));
    var unowned = new ArrayList<Path>();
    var disk =
        new Evidence.Disk() {
          @Override
          public void write(Path p, String text) throws java.io.IOException {
            Files.writeString(p, "unowned");
            unowned.add(p);
            throw new FileAlreadyExistsException(p.toString());
          }
        };
    assertThrows(
        FileAlreadyExistsException.class, () -> Evidence.scrub(root, new Redaction(), disk));
    assertEquals("unowned", Files.readString(unowned.getFirst()));
    Files.delete(unowned.getFirst());
    Files.writeString(root.resolve("b.ndjson"), "{broken}");
    assertThrows(java.io.IOException.class, () -> Evidence.scrub(root, new Redaction()));
    assertEquals("{broken}", Files.readString(root.resolve("b.ndjson")));
  }

  @Test
  void evidenceDirectoryMasqueradeAndEmptyNdjson() throws Exception {
    var root = Files.createTempDirectory("evidence-shapes");
    Files.writeString(root.resolve("empty.ndjson"), "");
    assertEquals(1, Evidence.verify(root, new Redaction()));
    Files.createDirectories(root.resolve("fake.ndjson"));
    assertThrows(java.io.IOException.class, () -> Evidence.scrub(root, new Redaction()));
  }

  @Test
  void strictSignatureOperationalFailureAndAbortedExecution() {
    assertThrows(
        java.io.IOException.class,
        () ->
            Baseline.check(
                Settings.load(Map.of(), "--baseline"),
                "BUG",
                Set.of("dev"),
                () -> fail(),
                () -> {
                  throw new java.io.IOException("signature transport");
                }));
    assertThrows(IllegalArgumentException.class, () -> Options.parse(Map.of(), "--examples", "0"));
    assertThrows(IllegalArgumentException.class, () -> Options.parse(Map.of(), "--seed", "bad"));
    assertThrows(
        IllegalArgumentException.class, () -> Options.parse(Map.of(), "--replay-index", "-1"));
    assertTrue(Options.parse(Map.of(), "--base-url", "http://localhost:3000").external());
  }
}
