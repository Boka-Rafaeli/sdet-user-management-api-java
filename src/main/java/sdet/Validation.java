package sdet;

import static org.junit.jupiter.api.Assertions.*;
import static sdet.Scenarios.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

final class Validation {
  record Mutation(String id, String field, String value, String postBug, String putBug) {
    void apply(ObjectNode p) {
      if (value == null) p.remove(field);
      else p.set(field, Json.parse(value));
    }
  }

  static final List<Mutation> MUTATIONS =
      List.of(
          new Mutation("missing-name", "name", null, null, null),
          new Mutation("missing-email", "email", null, null, null),
          new Mutation("missing-age", "age", null, null, null),
          new Mutation("null-name", "name", "null", null, null),
          new Mutation("null-email", "email", "null", null, null),
          new Mutation("null-age", "age", "null", null, null),
          new Mutation("name-integer", "name", "42", "BUG-006", "BUG-006"),
          new Mutation("email-integer", "email", "42", "BUG-006", "BUG-006"),
          new Mutation("invalid-email", "email", "\"not-an-email\"", "BUG-005", null),
          new Mutation("age-below-minimum", "age", "0", null, null),
          new Mutation("age-above-maximum", "age", "151", null, null),
          new Mutation("age-string", "age", "\"42\"", null, null),
          new Mutation("age-boolean", "age", "true", null, null),
          new Mutation("age-float", "age", "42.5", null, null),
          new Mutation("age-object", "age", "{\"value\":42}", null, null));

  private Validation() {}

  static void post(Builder b) {
    for (var m : MUTATIONS)
      b.add(
          "test_create_rejects_payloads_outside_openapi_schema[" + m.id() + "]",
          m.postBug(),
          c -> {
            var p = c.user();
            String original = p.path("email").asText();
            c.own(original);
            m.apply(p);
            if (p.hasNonNull("email")) c.own(p.get("email").asText());
            var r = c.client.create(p);
            if (r.status() == 400) {
              var users = c.list();
              absent(users, original);
              if (p.hasNonNull("email")) absent(users, p.get("email").asText());
            }
            c.check(
                () -> c.response(r, "/users", "post", 400),
                () -> {
                  assertEquals(201, r.status());
                  assertEquals("application/json", r.media());
                  assertEquals(p, r.json());
                  var persisted = c.client.get(p.path("email").asText());
                  assertEquals(200, persisted.status());
                  assertEquals("application/json", persisted.media());
                  var expected = p.deepCopy();
                  if (expected.path("name").isInt()) expected.put("name", "42");
                  if (expected.path("email").isInt()) expected.put("email", "42");
                  assertEquals(expected, persisted.json());
                });
          });
    for (int age : List.of(1, 150))
      b.add(
          "test_create_accepts_documented_age_boundaries["
              + (age == 1 ? "minimum" : "maximum")
              + "]",
          c -> c.create(c.user().put("age", age)));
    b.add(
        "test_create_accepts_empty_name_allowed_by_contract",
        "BUG-009",
        c -> {
          var p = c.user().put("name", "");
          String email = p.path("email").asText();
          c.own(email);
          var r = c.client.create(p);
          if (r.status() == 400) absent(c.list(), email);
          c.check(
              () -> assertEquals(p, c.response(r, "/users", "post", 201)),
              () ->
                  assertEquals(
                      Json.parse("{\"error\":\"name is required\"}"),
                      c.response(r, "/users", "post", 400)));
        });
  }
}
