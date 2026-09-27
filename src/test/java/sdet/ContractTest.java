package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractTest {
  static ApiClient.Response response(int status, String media, String body) {
    return new ApiClient.Response(
        status, Map.of("content-type", media), body.getBytes(StandardCharsets.UTF_8), null);
  }

  @Test
  void orderedChecksAndRefs() throws Exception {
    var c = new Contract();
    assertEquals(
        "x",
        c.response(
                response(400, "application/json; charset=utf-8", "{\"error\":\"x\"}"),
                "/users",
                "post",
                400)
            .get("error")
            .asText());
    assertThrows(
        AssertionError.class,
        () -> c.response(response(500, "application/json", "{}"), "/users", "post", 400));
    assertThrows(
        AssertionError.class,
        () -> c.response(response(400, "text/plain", "{}"), "/users", "post", 400));
    assertThrows(
        AssertionError.class,
        () -> c.response(response(400, "application/json", "invalid"), "/users", "post", 400));
    assertThrows(
        AssertionError.class,
        () -> c.response(response(400, "application/json", "{}"), "/users", "post", 400));
    assertThrows(
        AssertionError.class,
        () ->
            c.response(response(204, "application/json", "null"), "/users/{email}", "delete", 204));
    assertThrows(
        IllegalArgumentException.class,
        () -> c.resolve(Json.parse("{\"$ref\":\"https://evil.test/a\"}")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "\"42\"", "42.5", "0", "151", "null"})
  void strictNumericTypes(String age) throws Exception {
    var c = new Contract();
    var body = Json.parse("{\"name\":\"N\",\"email\":\"a@b\",\"age\":" + age + "}");
    var before = body.deepCopy();
    assertFalse(c.valid(c.document().at("/components/schemas/User"), body));
    assertEquals(before, body);
  }

  @Test
  void formatAndMutationWitnesses() throws Exception {
    var c = new Contract();
    var schema = c.document().at("/components/schemas/User");
    assertTrue(c.valid(schema, Json.parse("{\"name\":\"\",\"email\":\"@\",\"age\":1}")));
    assertFalse(c.valid(schema, Json.parse("{\"name\":\"N\",\"email\":\"invalid\",\"age\":1}")));
    var changed = c.document();
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            changed.at("/components/schemas/User/properties/age"))
        .put("maximum", 10);
    var stricter = new Contract(changed);
    assertFalse(
        stricter.valid(
            stricter.document().at("/components/schemas/User"),
            Json.parse("{\"name\":\"N\",\"email\":\"@\",\"age\":42}")));
    ((com.fasterxml.jackson.databind.node.ObjectNode) changed.at("/components/schemas/User"))
        .put("pattern", "x");
    assertThrows(IllegalArgumentException.class, () -> new Contract(changed));
  }
}
