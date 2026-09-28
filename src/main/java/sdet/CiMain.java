package sdet;

import java.nio.file.*;
import java.util.*;

public final class CiMain {
  private CiMain() {}

  private static Path state(String scope) {
    return Path.of(".runtime", scope + ".json");
  }

  private static com.fasterxml.jackson.databind.JsonNode read(String scope) throws Exception {
    return Json.parse(Files.readString(state(scope)));
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2 || !Set.of("dev", "prod", "isolation").contains(args[1]))
      throw new IllegalArgumentException(
          "Usage: CiMain <start|deterministic|generator|logs|stop|scrub> <scope>");
    String stage = args[0],
        scope = args[1],
        probe = System.getenv().getOrDefault("CI_PROBE", "none");
    Path root = Path.of("reports/run", scope);
    Files.createDirectories(root);
    var redact = new Redaction(System.getenv().getOrDefault("AUTH_TOKEN", "mysecrettoken"));
    try {
      switch (stage) {
        case "start" -> {
          var runtime = DockerRuntime.start();
          try {
            Files.createDirectories(state(scope).getParent());
            Files.writeString(
                state(scope),
                Json.text(
                    Map.of(
                        "id",
                        runtime.id(),
                        "baseUrl",
                        runtime.baseUrl().toString(),
                        "digest",
                        runtime.digest())));
            Files.writeString(root.resolve("image-digest.txt"), runtime.digest() + "\n");
            runtime.detach();
          } catch (Exception e) {
            runtime.close();
            throw e;
          }
        }
        case "deterministic" -> {
          var data = read(scope);
          var env = new HashMap<>(System.getenv());
          env.put("BASE_URL", data.path("baseUrl").asText());
          env.put("TEST_ENV", scope.equals("prod") ? "prod" : "dev");
          env.put("KNOWN_BUGS_AS_XFAIL", probe.equals("deterministic") ? "0" : "1");
          var settings = Settings.load(env, "--http-trace", "--contract-trace");
          if (!Verification.deterministic(scope, settings, root, data.path("digest").asText()))
            throw new IllegalStateException("Deterministic failure");
          if (probe.equals("deterministic"))
            throw new IllegalStateException("Controlled deterministic failure");
        }
        case "generator" -> {
          var data = read(scope);
          var settings =
              Settings.load(System.getenv())
                  .withBaseUrl(java.net.URI.create(data.path("baseUrl").asText()))
                  .withEnvironment(scope.equals("prod") ? "prod" : "dev");
          try {
            Verification.generated(
                settings,
                root,
                data.path("digest").asText(),
                Path.of(
                    probe.equals("generator")
                        ? ".runtime/intentional-missing-schema.yml"
                        : "openapi/sdet_challenge_api.yml"));
          } catch (Exception e) {
            Files.createDirectories(root.resolve("generated"));
            Files.writeString(
                root.resolve("generated/engine-error.json"),
                Json.text(Map.of("engineError", e.getClass().getSimpleName())));
            throw e;
          }
        }
        case "logs" -> {
          if (Files.exists(state(scope)))
            Files.writeString(
                root.resolve("container.log"),
                redact.text(
                    DockerRuntime.execute(List.of("logs", read(scope).path("id").asText()))));
        }
        case "stop" -> {
          if (Files.exists(state(scope))) {
            String id = read(scope).path("id").asText();
            if (!id.matches("[a-f0-9]{12,64}")) throw new IllegalStateException("Invalid owned ID");
            String owner =
                DockerRuntime.execute(
                        List.of(
                            "inspect",
                            "--format",
                            "{{index .Config.Labels \"sdet-java-owned\"}}",
                            id))
                    .trim();
            if (!owner.equals("true"))
              throw new IllegalStateException("Container ownership mismatch");
            DockerRuntime.execute(List.of("rm", "--force", id));
            Files.delete(state(scope));
            Files.writeString(
                root.resolve("cleanup.json"),
                Json.text(Map.of("containerId", id, "removed", true)));
          }
        }
        case "scrub" -> {
          if (probe.equals("scrub")) Files.writeString(root.resolve("probe.ndjson"), "{malformed}");
          Evidence.scrub(root, redact);
          System.out.println("Verified evidence files=" + Evidence.verify(root, redact));
        }
        default -> throw new IllegalArgumentException("Unknown CI stage");
      }
    } catch (Exception e) {
      System.err.println("CI " + stage + " failed: " + e.getClass().getSimpleName());
      throw new IllegalStateException("CI stage failed");
    }
  }
}
