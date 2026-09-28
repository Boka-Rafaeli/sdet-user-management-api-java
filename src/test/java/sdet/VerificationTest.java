package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class VerificationTest {
  @Test
  void failureDoesNotSkipLaterStages() {
    var stages = new ArrayList<Integer>();
    assertFalse(
        Verification.all(
            List.of(
                () -> {
                  stages.add(1);
                  return false;
                },
                () -> {
                  stages.add(2);
                  throw new java.io.IOException("engine");
                },
                () -> {
                  stages.add(3);
                  return true;
                })));
    assertEquals(List.of(1, 2, 3), stages);
  }

  @Test
  void exactManifestAndNoDuplicateIds() throws Exception {
    for (String scope : List.of("dev", "prod", "isolation")) {
      var settings = Settings.load(Map.of()).withEnvironment(scope.equals("prod") ? "prod" : "dev");
      try (var dev = new ApiClient(settings.withEnvironment("dev"));
          var prod = new ApiClient(settings.withEnvironment("prod"))) {
        var cases = Scenarios.build(scope, settings, dev, prod, new Contract());
        assertEquals(scope.equals("isolation") ? 1 : 55, cases.size());
        assertEquals(
            new HashSet<>(Verification.expected(scope)),
            new HashSet<>(cases.stream().map(SuiteRunner.Case::id).toList()));
      }
    }
  }
}
