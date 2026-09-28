package sdet;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.nio.charset.StandardCharsets;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

class PlatformTest {
  public static class JupiterFixture {
    @Test
    void succeeds() {
      assertEquals(4, 2 + 2);
    }
  }

  public static class PropertyFixture {
    @Property(tries = 40, seed = "424242")
    boolean roundTrip(@ForAll int value) {
      return Integer.parseInt(Integer.toString(value)) == value;
    }
  }

  public static class ShrinkFixture {
    @Property(tries = 100, seed = "424242")
    boolean controlledFailure(
        @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 1000) int value) {
      return value < 10;
    }
  }

  public static class Probe {
    public static void main(String[] args) {
      var listener = new SummaryGeneratingListener();
      var launcher = LauncherFactory.create();
      launcher.registerTestExecutionListeners(listener);
      launcher.execute(
          LauncherDiscoveryRequestBuilder.request().selectors(selectClass(args[0])).build());
      var s = listener.getSummary();
      System.out.println(
          "EXECUTED=" + s.getTestsStartedCount() + " FAILED=" + s.getTestsFailedCount());
      System.exit(
          s.getTestsStartedCount() == 0
                  || s.getTestsFailedCount() > 0
                  || s.getTestsAbortedCount() > 0
              ? 1
              : 0);
    }
  }

  record Result(int code, String output) {}

  static Result run(Class<?> fixture) throws Exception {
    var process =
        JvmSupport.process(Probe.class, fixture.getName()).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Result(process.waitFor(), output);
  }

  @Test
  void jupiterRunsInRealJvm() throws Exception {
    assertEquals(0, run(JupiterFixture.class).code());
  }

  @Test
  void propertyEngineRunsInRealJvm() throws Exception {
    var r = run(PropertyFixture.class);
    assertEquals(0, r.code(), r.output());
    assertTrue(r.output().contains("EXECUTED=1"));
  }

  @Test
  void shrinkingAndSeedReplayInRealJvm() throws Exception {
    var first = run(ShrinkFixture.class);
    var second = run(ShrinkFixture.class);
    assertEquals(1, first.code());
    assertEquals(1, second.code());
    assertTrue(first.output().contains("arg0: 10"), first.output());
    assertTrue(second.output().contains("arg0: 10"), second.output());
  }
}
