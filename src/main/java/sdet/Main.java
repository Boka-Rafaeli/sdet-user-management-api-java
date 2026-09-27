package sdet;

import java.nio.file.*;
import java.util.*;

public final class Main {
  private Main() {}

  public static void main(String[] args) throws Exception {
    String command = args.length == 0 ? "verify" : args[0];
    var settings =
        Settings.load(
            System.getenv(), Arrays.copyOfRange(args, Math.min(1, args.length), args.length));
    boolean ok = true;
    for (String scope :
        command.equals("verify") ? List.of("dev", "prod", "isolation") : List.of(command)) {
      if (!Set.of("dev", "prod", "isolation").contains(scope))
        throw new IllegalArgumentException("Unknown scope");
      try (var runtime = DockerRuntime.start()) {
        var configured =
            settings
                .withBaseUrl(runtime.baseUrl())
                .withEnvironment(scope.equals("prod") ? "prod" : "dev");
        try (var dev = new ApiClient(configured.withEnvironment("dev"));
            var prod = new ApiClient(configured.withEnvironment("prod"))) {
          var cases = Scenarios.build(scope, configured, dev, prod, new Contract());
          if (cases.isEmpty()) continue;
          boolean result =
              SuiteRunner.run(
                  cases,
                  cases.stream().map(SuiteRunner.Case::id).toList(),
                  Path.of("reports/run", scope, "deterministic"),
                  scope,
                  runtime.digest());
          ok = result && ok;
          System.out.println(scope + ": " + cases.size() + " cases, complete=" + result);
        }
      } catch (Exception e) {
        ok = false;
        System.err.println(scope + ": " + e.getClass().getSimpleName());
      }
    }
    if (!ok) throw new IllegalStateException("Verification failed; inspect reports/run");
  }
}
