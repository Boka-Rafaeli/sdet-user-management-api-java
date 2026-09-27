package sdet;

import static org.junit.jupiter.api.Assertions.*;
import static sdet.Scenarios.*;

final class Crud {
  private Crud() {}

  static void add(Builder b) {
    b.add("test_list_users_matches_contract", c -> c.list());
    b.add(
        "test_create_get_and_list_user",
        c -> {
          var p = c.create();
          assertEquals(p, c.read(p.path("email").asText()));
          contains(c.list(), p);
        });
    b.add(
        "test_delete_user_returns_no_content_and_removes_record",
        c -> {
          var p = c.create();
          String email = p.path("email").asText();
          c.response(c.client.delete(email), "/users/{email}", "delete", 204);
          absent(c.list(), email);
        });
    b.add(
        "test_duplicate_email_returns_conflict",
        "BUG-003",
        c -> {
          var p = c.create();
          var r = c.client.create(p);
          c.unchanged(p);
          contains(c.list(), p);
          c.check(() -> c.response(r, "/users", "post", 409), () -> internalError(r));
        });
    b.add(
        "test_get_unknown_user_returns_not_found",
        "BUG-004",
        c -> {
          var r = c.client.get(c.user().path("email").asText());
          c.check(() -> c.response(r, "/users/{email}", "get", 404), () -> internalError(r));
        });
  }
}
