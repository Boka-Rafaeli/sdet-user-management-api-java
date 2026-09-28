package sdet;

import static org.junit.jupiter.api.Assertions.*;
import static sdet.Scenarios.*;

final class Isolation {
  private Isolation() {}

  static void add(Builder b) {
    b.add(
        "test_dev_and_prod_data_is_independent",
        c -> {
          String email = c.user().path("email").asText();
          var dev = c.user().put("name", "Development User").put("email", email).put("age", 31);
          var prod = c.user().put("name", "Production User").put("email", email).put("age", 47);
          c.owned.add("dev", email);
          c.owned.add("prod", email);
          assertEquals(dev, c.response(c.dev.create(dev), "/users", "post", 201));
          assertEquals(prod, c.response(c.prod.create(prod), "/users", "post", 201));
          c.response(
              c.dev.update(
                  email,
                  c.user().put("name", "Updated Dev User").put("email", email).put("age", 32)),
              "/users/{email}",
              "put",
              200);
          assertEquals(prod, c.response(c.prod.get(email), "/users/{email}", "get", 200));
          c.response(c.dev.delete(email), "/users/{email}", "delete", 204);
          absent(c.response(c.dev.list(), "/users", "get", 200), email);
          assertEquals(prod, c.response(c.prod.get(email), "/users/{email}", "get", 200));
        });
  }
}
