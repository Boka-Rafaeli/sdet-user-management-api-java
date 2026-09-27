package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;

class OwnedUsersTest {
  @Test
  void onlyOwnedDeduplicatedEnvironmentKeys() throws Exception {
    var seen = new ArrayList<String>();
    var users = new OwnedUsers((env, email) -> seen.add(env + ":" + email));
    users.add("dev", "a@b");
    users.add("dev", "a@b");
    users.add("prod", "a@b");
    users.cleanup();
    users.cleanup();
    assertEquals(List.of("dev:a@b", "prod:a@b"), seen);
  }

  @Test
  void cleanupContinuesAfterFailure() {
    var seen = new ArrayList<String>();
    var users =
        new OwnedUsers(
            (env, email) -> {
              seen.add(email);
              if (email.equals("a")) throw new IOException("connection");
            });
    users.add("dev", "a");
    users.add("dev", "b");
    assertThrows(IOException.class, users::cleanup);
    assertEquals(List.of("a", "b"), seen);
  }

  @Test
  void independentUniqueData() {
    assertNotEquals(OwnedUsers.user("dev").get("email"), OwnedUsers.user("dev").get("email"));
    assertEquals(42, OwnedUsers.user("prod").get("age").intValue());
  }
}
