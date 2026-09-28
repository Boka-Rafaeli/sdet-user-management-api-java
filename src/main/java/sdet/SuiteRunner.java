package sdet;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;

public final class SuiteRunner {
  public record Case(String id, Baseline.Action body, Baseline.Action cleanup) {}

  private static final ThreadLocal<List<Case>> CASES = new ThreadLocal<>();
  private static final ThreadLocal<Map<String, String>> DEFECTS =
      ThreadLocal.withInitial(HashMap::new);

  public static final class Suite {
    @TestFactory
    Stream<DynamicTest> scenarios() {
      return CASES.get().stream()
          .map(
              c ->
                  DynamicTest.dynamicTest(
                      c.id(),
                      () -> {
                        String bug = Baseline.execute(c.body(), c.cleanup());
                        if (!bug.isEmpty()) DEFECTS.get().put(c.id(), bug);
                      }));
    }
  }

  private SuiteRunner() {}

  public static boolean run(
      List<Case> cases, List<String> expected, Path output, String scope, String digest)
      throws Exception {
    var entries = new ArrayList<Reports.Entry>();
    var infrastructure = new ArrayList<String>();
    CASES.set(cases);
    DEFECTS.get().clear();
    try {
      var launcher = LauncherFactory.create();
      launcher.registerTestExecutionListeners(
          new TestExecutionListener() {
            @Override
            public void executionSkipped(TestIdentifier id, String reason) {
              if (id.isTest())
                entries.add(new Reports.Entry(id.getDisplayName(), "SKIP", "", reason));
              else infrastructure.add("Skipped container");
            }

            @Override
            public void executionFinished(TestIdentifier id, TestExecutionResult result) {
              if (!id.isTest()) {
                if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL)
                  infrastructure.add("Container failed");
                return;
              }
              String name = id.getDisplayName();
              String bug = DEFECTS.get().getOrDefault(name, "");
              String status =
                  switch (result.getStatus()) {
                    case SUCCESSFUL -> bug.isEmpty() ? "PASS" : "XFAIL";
                    case ABORTED -> "ABORTED";
                    case FAILED -> "FAIL";
                  };
              entries.add(
                  new Reports.Entry(
                      name,
                      status,
                      bug,
                      result
                          .getThrowable()
                          .map(
                              t ->
                                  t.getClass().getSimpleName()
                                      + ": "
                                      + Objects.toString(t.getMessage(), ""))
                          .orElse("")));
            }
          });
      launcher.execute(
          LauncherDiscoveryRequestBuilder.request()
              .selectors(selectClass(Suite.class))
              .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
              .build());
    } finally {
      CASES.remove();
      DEFECTS.remove();
    }
    for (String error : infrastructure)
      entries.add(new Reports.Entry("infrastructure", "FAIL", "", error));
    var actual = entries.stream().map(Reports.Entry::id).toList();
    if (expected.isEmpty()
        || expected.size() != new HashSet<>(expected).size()
        || actual.size() != expected.size()
        || actual.size() != new HashSet<>(actual).size()
        || !new HashSet<>(actual).equals(new HashSet<>(expected)))
      entries.add(
          new Reports.Entry(
              "manifest-completeness", "FAIL", "", "Collected/executed IDs differ from manifest"));
    Reports.write(output, scope, digest, expected, entries);
    return Reports.complete(expected, entries);
  }
}
