package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class QualityTest {
  @Test
  void resolvedJarChecksumsAndSourceHashes() throws Exception {
    var paths =
        Arrays.stream(
                System.getProperty("surefire.test.class.path").split(java.io.File.pathSeparator))
            .map(Path::of)
            .toList();
    assertEquals(
        Json.parse(Files.readString(Path.of("docs/dependencies-sha256.json"))),
        Json.MAPPER.valueToTree(Dependencies.inventory(paths)));
    var provenance = Json.parse(Files.readString(Path.of("docs/provenance.json")));
    assertEquals(
        provenance.path("openapiSha256").asText(),
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(Path.of("openapi/sdet_challenge_api.yml")))));
    var wrappers = Json.parse(Files.readString(Path.of("docs/wrapper-sha256.json")));
    var fields = wrappers.fields();
    while (fields.hasNext()) {
      var field = fields.next();
      assertEquals(
          field.getValue().asText(),
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(Files.readAllBytes(Path.of(field.getKey())))));
    }
  }

  @Test
  void pinnedActionsMinimalPermissionsAndFailureContinuation() throws Exception {
    try (var files = Files.list(Path.of(".github/workflows"))) {
      for (var file : files.toList()) {
        var source = Files.readString(file);
        var matcher = java.util.regex.Pattern.compile("uses: ([^\\s]+)").matcher(source);
        while (matcher.find()) assertTrue(matcher.group(1).matches("[^@]+@[a-f0-9]{40}"));
        assertTrue(source.contains("persist-credentials: false"));
        assertTrue(source.contains("contents: read"));
      }
    }
    String workflow = Files.readString(Path.of(".github/workflows/api.yml"));
    assertTrue(workflow.contains("fail-fast: false"));
    assertTrue(workflow.contains("always() && steps.start.outcome == 'success'"));
    assertTrue(workflow.contains("steps.scrub.outcome == 'success'"));
    assertTrue(workflow.contains("retention-days: 14"));
  }
}
