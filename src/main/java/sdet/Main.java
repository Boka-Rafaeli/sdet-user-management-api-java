package sdet;

import java.nio.file.*;
import java.util.*;

public final class Main {
  private Main() {}

  public static void main(String[] args) throws Exception {
    String command = args.length == 0 ? "verify" : args[0];
    var options =
        Options.parse(
            System.getenv(), Arrays.copyOfRange(args, Math.min(1, args.length), args.length));
    boolean ok = true;
    if (command.equals("scrub")) {
      var root = Path.of("reports/run");
      var redact = new Redaction(options.settings().token());
      Evidence.scrub(root, redact);
      System.out.println("Verified files=" + Evidence.verify(root, redact));
      return;
    }
    for (String scope :
        command.equals("verify")
            ? List.of("dev", "prod", "isolation")
            : List.of(command.replace("generated-", ""))) {
      if (!Set.of("dev", "prod", "isolation").contains(scope))
        throw new IllegalArgumentException("Unknown scope");
      DockerRuntime runtime = null;
      Path root = Path.of("reports/run", scope);
      Files.createDirectories(root);
      var redact = new Redaction(options.settings().token());
      try {
        var settings = options.settings().withEnvironment(scope.equals("prod") ? "prod" : "dev");
        String digest = "external-unverified";
        if (command.equals("verify") || !options.external()) {
          runtime = DockerRuntime.start();
          settings = settings.withBaseUrl(runtime.baseUrl());
          digest = runtime.digest();
          Files.writeString(root.resolve("image-digest.txt"), digest + "\n");
        }
        var configured = settings;
        String image = digest;
        var stages = new ArrayList<Verification.Stage>();
        if (!command.startsWith("generated-") && options.replay() == null)
          stages.add(() -> Verification.deterministic(scope, configured, root, image));
        if (!scope.equals("isolation"))
          stages.add(
              () -> {
                try (var client = new ApiClient(configured)) {
                  var explorer =
                      new Exploration(
                          new Generation(new Contract(options.schema())), client, options.limits());
                  if (options.replay() == null) {
                    var result = explorer.run(root.resolve("generated"), image);
                    System.out.println(
                        scope + ": generated=" + result.cases() + " stop=" + result.stopReason());
                  } else {
                    var text = Files.readString(options.replay());
                    var lines = text.lines().filter(line -> !line.isBlank()).toList();
                    var record =
                        Json.parse(
                            options.replay().toString().endsWith(".ndjson")
                                ? lines.get(options.replayIndex())
                                : text);
                    if (record.has("generator")
                        && !record.path("generator").asText().equals(Fuzzing.VERSION))
                      throw new IllegalArgumentException("Replay generator version mismatch");
                    var request =
                        record.has("minimal")
                            ? record.get("minimal")
                            : record.has("request") ? record.get("request") : record;
                    var checks =
                        explorer.replay(
                            request, record.path("sequence").asInt(options.replayIndex()));
                    Files.writeString(
                        root.resolve("replay.json"),
                        Json.text(
                            redact.json(
                                Json.MAPPER.valueToTree(
                                    Map.of(
                                        "generator",
                                        Fuzzing.VERSION,
                                        "request",
                                        request,
                                        "checks",
                                        checks)))));
                    System.out.println("Replay checks: " + checks);
                  }
                  return true;
                }
              });
        ok = Verification.all(stages) && ok;
      } catch (Exception e) {
        ok = false;
        Files.writeString(
            root.resolve("engine-error.json"),
            Json.text(Map.of("error", e.getClass().getSimpleName())));
        System.err.println(scope + ": " + e.getClass().getSimpleName());
      } finally {
        if (runtime != null) {
          try {
            Files.writeString(root.resolve("container.log"), redact.text(runtime.logs()));
          } catch (Exception e) {
            ok = false;
          } finally {
            try {
              String id = runtime.id();
              runtime.close();
              Files.writeString(
                  root.resolve("cleanup.json"),
                  Json.text(Map.of("containerId", id, "removed", true)));
            } catch (Exception e) {
              ok = false;
            }
          }
        }
        try {
          Evidence.scrub(root, redact);
          Evidence.verify(root, redact);
        } catch (Exception e) {
          ok = false;
          System.err.println("Evidence gate failed for " + scope);
        }
      }
    }
    if (!ok) throw new IllegalStateException("Verification failed; inspect reports/run");
  }
}
