package sdet;

import static org.junit.jupiter.api.Assertions.*;
import static sdet.Scenarios.*;

import java.util.*;

final class Authentication {
  private Authentication() {}

  static void add(Builder b) {
    for (String kind : List.of("missing-header", "empty-token", "invalid-token"))
      b.add(
          "test_delete_rejects_missing_or_invalid_authentication[" + kind + "]",
          "BUG-001",
          Set.of("dev"),
          c -> {
            var original = c.create();
            String email = original.path("email").asText();
            String token =
                switch (kind) {
                  case "missing-header" -> null;
                  case "empty-token" -> "";
                  default -> "wrong-token";
                };
            var r = c.client.delete(email, token);
            if (r.status() == 401) c.unchanged(original);
            c.check(
                () -> {
                  c.response(r, "/users/{email}", "delete", 401);
                  assertEquals(original, c.read(email));
                },
                () -> {
                  c.response(r, "/users/{email}", "delete", 204);
                  absent(c.list(), email);
                });
          });
    b.add(
        "test_delete_unknown_user_with_valid_token_returns_not_found",
        c ->
            c.response(
                c.client.delete(c.user().path("email").asText()), "/users/{email}", "delete", 404));
  }
}
