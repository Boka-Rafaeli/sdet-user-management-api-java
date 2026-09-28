package sdet;

import static org.junit.jupiter.api.Assertions.*;
import static sdet.Scenarios.*;

final class Updates {
  private Updates() {}

  static void add(Builder b) {
    b.add(
        "test_update_persists_changes",
        "BUG-002",
        c -> {
          var original = c.create();
          String email = original.path("email").asText();
          var updated =
              c.user().put("name", "Updated Candidate").put("email", email).put("age", 43);
          assertEquals(
              updated, c.response(c.client.update(email, updated), "/users/{email}", "put", 200));
          var persisted = c.read(email);
          c.check(() -> assertEquals(updated, persisted), () -> assertEquals(original, persisted));
        });
    b.add(
        "test_update_persists_email_change",
        "BUG-002",
        c -> {
          var original = c.create();
          String old = original.path("email").asText();
          var updated = c.user().put("name", "Renamed Candidate").put("age", 43);
          String email = updated.path("email").asText();
          c.own(email);
          assertEquals(
              updated, c.response(c.client.update(old, updated), "/users/{email}", "put", 200));
          var oldKey = c.client.get(old);
          var newKey = c.client.get(email);
          var listed = c.client.list();
          c.check(
              () -> {
                assertEquals(updated, c.response(newKey, "/users/{email}", "get", 200));
                c.response(oldKey, "/users/{email}", "get", 404);
                var users = c.response(listed, "/users", "get", 200);
                contains(users, updated);
                absent(users, old);
              },
              () -> {
                assertEquals(original, c.response(oldKey, "/users/{email}", "get", 200));
                internalError(newKey);
                var users = c.response(listed, "/users", "get", 200);
                contains(users, original);
                absent(users, email);
              });
        });
    b.add(
        "test_update_unknown_user_returns_not_found",
        c -> {
          String email = c.user().path("email").asText();
          c.own(email);
          c.response(
              c.client.update(email, c.user().put("email", email)), "/users/{email}", "put", 404);
          absent(c.list(), email);
        });
    b.add(
        "test_update_to_duplicate_email_returns_conflict",
        c -> {
          var first = c.create(c.user().put("name", "First User"));
          var second = c.create(c.user().put("name", "Second User"));
          c.response(
              c.client.update(
                  first.path("email").asText(),
                  c.user().put("name", "Changed User").put("email", second.path("email").asText())),
              "/users/{email}",
              "put",
              409);
          c.unchanged(first);
          c.unchanged(second);
        });
  }
}
