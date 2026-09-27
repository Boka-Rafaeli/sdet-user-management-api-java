package sdet;

import java.util.Set;

public final class Baseline {
  private Baseline() {}

  @FunctionalInterface
  public interface Action {
    void run() throws Exception;
  }

  public static final class KnownDefect extends Exception {
    private final String bug;

    public KnownDefect(String bug) {
      super(bug + ": exact defect signature reproduced");
      this.bug = bug;
    }

    public String bug() {
      return bug;
    }
  }

  public static void check(
      Settings settings, String bug, Set<String> affected, Action expected, Action signature)
      throws Exception {
    if (bug == null || !settings.baseline() || !affected.contains(settings.environment())) {
      expected.run();
      return;
    }
    try {
      expected.run();
    } catch (AssertionError error) {
      signature.run();
      throw new KnownDefect(bug);
    }
    throw new IllegalStateException(bug + ": expected behavior restored; remove baseline");
  }

  public static String execute(Action body, Action cleanup) throws Exception {
    KnownDefect defect = null;
    try {
      body.run();
    } catch (KnownDefect known) {
      defect = known;
    } finally {
      cleanup.run();
    }
    return defect == null ? "" : defect.bug();
  }
}
