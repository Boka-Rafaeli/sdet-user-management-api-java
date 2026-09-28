package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CiLifecycleTest {
  private int stage(Path cwd, String command, String probe) throws Exception {
    var process =
        JvmSupport.process(CiMain.class, command, "dev")
            .directory(cwd.toFile())
            .redirectErrorStream(true)
            .start();
    String output =
        new String(
            process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    int code = process.waitFor();
    Files.writeString(cwd.resolve(command + "-" + probe + ".log"), output);
    return code;
  }

  private int probe(Path cwd, String command, String probe) throws Exception {
    var builder =
        JvmSupport.process(CiMain.class, command, "dev")
            .directory(cwd.toFile())
            .redirectErrorStream(true);
    builder.environment().put("CI_PROBE", probe);
    var process = builder.start();
    String output =
        new String(
            process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    int code = process.waitFor();
    Files.writeString(cwd.resolve(command + "-" + probe + ".log"), output);
    return code;
  }

  @Test
  void realProcessesContinueAfterFailuresAndFailClosedUpload() throws Exception {
    Path root = Files.createTempDirectory("ci-lifecycle");
    Files.createDirectories(root.resolve("openapi"));
    Files.createDirectories(root.resolve("docs"));
    Files.copy(
        Path.of("openapi/sdet_challenge_api.yml"), root.resolve("openapi/sdet_challenge_api.yml"));
    Files.copy(Path.of("docs/api-manifest.json"), root.resolve("docs/api-manifest.json"));
    assertEquals(0, stage(root, "start", "none"));
    try {
      assertNotEquals(0, probe(root, "deterministic", "deterministic"));
      assertEquals(0, stage(root, "generator", "none"));
      assertTrue(Files.exists(root.resolve("reports/run/dev/generated/events.ndjson")));
      assertNotEquals(0, probe(root, "generator", "generator"));
      assertEquals(0, stage(root, "logs", "none"));
    } finally {
      assertEquals(0, stage(root, "stop", "none"));
    }
    assertTrue(
        Json.parse(Files.readString(root.resolve("reports/run/dev/cleanup.json")))
            .path("removed")
            .asBoolean());
    assertEquals(0, stage(root, "scrub", "none"));
    assertNotEquals(0, probe(root, "scrub", "scrub"));
    assertFalse(Files.exists(root.resolve(".runtime/dev.json")));
  }
}
