package sdet;

import static org.junit.jupiter.api.Assertions.*;
import static sdet.Scenarios.*;

final class PutValidation {
  private PutValidation() {}

  static void add(Builder b) {
    for (var m : Validation.MUTATIONS)
      b.add(
          "test_update_rejects_payloads_outside_openapi_schema[" + m.id() + "]",
          m.putBug(),
          c -> {
            var original = c.create();
            String email = original.path("email").asText();
            var update = c.user().put("email", email);
            m.apply(update);
            if (update.hasNonNull("email")) c.own(update.get("email").asText());
            var r = c.client.update(email, update);
            c.unchanged(original);
            c.check(
                () -> c.response(r, "/users/{email}", "put", 400),
                () -> {
                  assertEquals("application/json", r.media());
                  if (update.path("email").isInt()) internalError(r);
                  else {
                    assertEquals(42, update.path("name").intValue());
                    assertEquals(200, r.status());
                    assertEquals(update, r.json());
                  }
                });
          });
    b.add(
        "test_update_accepts_empty_name_allowed_by_contract",
        "BUG-009",
        c -> {
          var original = c.create();
          String email = original.path("email").asText();
          var update = c.user().put("name", "").put("email", email).put("age", 43);
          var r = c.client.update(email, update);
          if (r.status() == 400) c.unchanged(original);
          c.check(
              () -> assertEquals(update, c.response(r, "/users/{email}", "put", 200)),
              () ->
                  assertEquals(
                      Json.parse("{\"error\":\"name is required\"}"),
                      c.response(r, "/users/{email}", "put", 400)));
        });
  }
}
