package sdet;

import java.nio.file.*;
import java.util.*;

public final class Verification {
  private Verification() {}

  public static List<String> expected(String scope) throws java.io.IOException {
    var manifest =
        Json.parse(Files.readString(Path.of("docs/api-manifest.json")))
            .get(scope.equals("isolation") ? "isolation" : "api");
    if (manifest == null || !manifest.isArray() || manifest.isEmpty())
      throw new IllegalStateException("Missing expected IDs");
    var ids = new ArrayList<String>();
    manifest.forEach(n -> ids.add(n.asText()));
    return ids;
  }

  public static boolean deterministic(String scope, Settings configured, Path root, String digest)
      throws Exception {
    try (var dev = new ApiClient(configured.withEnvironment("dev"));
        var prod = new ApiClient(configured.withEnvironment("prod"))) {
      var cases = Scenarios.build(scope, configured, dev, prod, new Contract());
      var expected = expected(scope);
      if (cases.size() != expected.size()
          || !new HashSet<>(cases.stream().map(SuiteRunner.Case::id).toList())
              .equals(new HashSet<>(expected)))
        throw new IllegalStateException("Collected IDs differ from source manifest");
      return SuiteRunner.run(cases, expected, root.resolve("deterministic"), scope, digest);
    }
  }

  public static Exploration.Result generated(
      Settings configured, Path root, String digest, Path schema) throws Exception {
    try (var client = new ApiClient(configured)) {
      return new Exploration(
              new Generation(new Contract(schema)), client, Exploration.Limits.defaults())
          .run(root.resolve("generated"), digest);
    }
  }

  @FunctionalInterface
  public interface Stage {
    boolean run() throws Exception;
  }

  public static boolean all(List<Stage> stages) {
    boolean ok = true;
    for (var stage : stages)
      try {
        ok = stage.run() && ok;
      } catch (Exception e) {
        ok = false;
        System.err.println("Stage failed: " + e.getClass().getSimpleName());
      }
    return ok;
  }
}
