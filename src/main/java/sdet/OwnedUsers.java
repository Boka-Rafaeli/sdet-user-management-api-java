package sdet;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

public final class OwnedUsers {
  @FunctionalInterface
  public interface Delete {
    void delete(String environment, String email) throws Exception;
  }

  private final Map<String, Set<String>> owned = new LinkedHashMap<>();
  private final Delete delete;

  public OwnedUsers(Delete delete) {
    this.delete = delete;
  }

  public void add(String environment, String email) {
    owned.computeIfAbsent(environment, k -> new LinkedHashSet<>()).add(email);
  }

  public void cleanup() throws Exception {
    Exception failure = null;
    for (var entry : owned.entrySet())
      for (var email : new ArrayList<>(entry.getValue()))
        try {
          delete.delete(entry.getKey(), email);
          entry.getValue().remove(email);
        } catch (Exception e) {
          if (failure == null) failure = e;
          else failure.addSuppressed(e);
        }
    if (failure != null) throw failure;
  }

  public static ObjectNode user(String environment) {
    return Json.MAPPER
        .createObjectNode()
        .put("name", "SDET Candidate")
        .put(
            "email",
            "sdet-"
                + environment
                + "-"
                + UUID.randomUUID().toString().replace("-", "")
                + "@example.com")
        .put("age", 42);
  }
}
