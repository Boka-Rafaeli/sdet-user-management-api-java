package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DocumentationTest {
  @Test
  void allReferencePurposesHaveJavaEvidence() throws Exception {
    var rows = Json.parse(Files.readString(Path.of("docs/infrastructure-parity.json")));
    int unit = 0, infra = 0;
    for (var row : rows) {
      if (row.path("suite").asText().equals("unit")) unit++;
      else infra++;
      assertFalse(row.path("purpose").asText().isBlank());
      assertFalse(row.path("javaEvidence").isEmpty());
      for (var file : row.path("javaEvidence"))
        assertTrue(Files.exists(Path.of(file.asText())), file.asText());
    }
    assertEquals(128, unit);
    assertEquals(10, infra);
  }

  @Test
  void samplesHaveVerifiedProvenanceAndPerIdParity() throws Exception {
    var provenance = Json.parse(Files.readString(Path.of("reports/sample/provenance.json")));
    assertTrue(provenance.path("implementationCommit").asText().matches("[a-f0-9]{40}"));
    assertEquals(DockerRuntime.IMAGE, provenance.path("image").asText());
    var parity = Json.parse(Files.readString(Path.of("docs/api-parity-results.json")));
    assertEquals(111, parity.path("results").size());
    for (var row : parity.path("results")) {
      assertEquals(row.path("typescript"), row.path("java"));
      var report =
          Json.parse(
              Files.readString(
                  Path.of(
                      "reports/sample", row.path("scope").asText(), "deterministic/summary.json")));
      var found = new ArrayList<String>();
      for (var entry : report.path("results"))
        if (entry.path("id").equals(row.path("id"))) found.add(entry.path("status").asText());
      assertEquals(List.of(row.path("java").asText()), found);
    }
    assertTrue(Evidence.verify(Path.of("reports/sample"), new Redaction("mysecrettoken")) > 10);
  }

  @Test
  void realCiEvidenceAndManualAssetsRemainAccessible() throws Exception {
    var probes = Json.parse(Files.readString(Path.of("docs/ci-probes.json")));
    assertTrue(probes.size() >= 3);
    for (var probe : probes) {
      assertEquals("failure", probe.path("conclusion").asText());
      assertTrue(
          probe
              .path("url")
              .asText()
              .startsWith(
                  "https://github.com/Boka-Rafaeli/sdet-user-management-api-java/actions/runs/"));
    }
    assertTrue(Files.exists(Path.of("manual/postman/SDET_Interview.postman_collection.json")));
    Json.parse(Files.readString(Path.of("manual/postman/SDET_Interview.postman_collection.json")));
  }
}
