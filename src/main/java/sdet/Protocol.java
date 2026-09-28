package sdet;

import static org.junit.jupiter.api.Assertions.*;
import static sdet.Scenarios.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

final class Protocol {
  private Protocol() {}

  static void add(Builder b) {
    for (String shape : List.of("array", "string")) {
      var body = Json.parse(shape.equals("array") ? "[]" : "\"text\"");
      b.add(
          "test_create_rejects_non_object_json_body[" + shape + "]",
          "BUG-007",
          c -> {
            var r = c.client.create(body);
            c.check(() -> c.response(r, "/users", "post", 400), () -> internalError(r));
          });
      b.add(
          "test_update_rejects_non_object_json_body[" + shape + "]",
          "BUG-007",
          c -> {
            var original = c.create();
            var r = c.client.update(original.path("email").asText(), body);
            c.unchanged(original);
            c.check(() -> c.response(r, "/users/{email}", "put", 400), () -> internalError(r));
          });
    }
    b.add(
        "test_create_rejects_unsupported_request_media_type",
        "BUG-008",
        c -> {
          var p = c.user();
          String email = p.path("email").asText();
          c.own(email);
          var r =
              c.client.raw(
                  "POST",
                  "/users",
                  Json.text(p).getBytes(StandardCharsets.UTF_8),
                  Map.of("Content-Type", "text/plain"));
          if (r.status() == 400) absent(c.list(), email);
          c.check(
              () -> c.response(r, "/users", "post", 400),
              () -> {
                assertEquals(p, c.response(r, "/users", "post", 201));
                assertEquals(p, c.read(email));
              });
        });
    b.add(
        "test_update_rejects_unsupported_request_media_type",
        "BUG-008",
        c -> {
          var original = c.create();
          String email = original.path("email").asText();
          var update = c.user().put("name", "Wrong Media Type").put("email", email).put("age", 43);
          var r =
              c.client.raw(
                  "PUT",
                  "/users/" + ApiClient.encode(email),
                  Json.text(update).getBytes(StandardCharsets.UTF_8),
                  Map.of("Content-Type", "text/plain"));
          c.unchanged(original);
          c.check(
              () -> c.response(r, "/users/{email}", "put", 400),
              () -> assertEquals(update, c.response(r, "/users/{email}", "put", 200)));
        });
    for (String kind : List.of("plus", "percent"))
      b.add(
          "test_email_reserved_character_round_trips_through_encoded_path[" + kind + "]",
          c -> {
            var p = c.user();
            String email =
                p.path("email").asText().replace("@", (kind.equals("plus") ? "+" : "%") + "tag@");
            p.put("email", email);
            c.create(p);
            var read = c.client.get(email);
            String encoded = kind.equals("plus") ? "%2B" : "%25";
            assertTrue(read.request().uri().getRawPath().contains(encoded));
            assertEquals(p, c.response(read, "/users/{email}", "get", 200));
            var deleted = c.client.delete(email);
            assertTrue(deleted.request().uri().getRawPath().contains(encoded));
            c.response(deleted, "/users/{email}", "delete", 204);
            absent(c.list(), email);
          });
  }
}
