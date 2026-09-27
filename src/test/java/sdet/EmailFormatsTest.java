package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;

class EmailFormatsTest {
  @TestFactory
  Stream<DynamicTest> goldenCorpus() throws Exception {
    var corpus =
        Json.parse(Files.readString(Path.of("src/test/resources/generated-email-corpus.json")))
            .get("cases");
    assertEquals(710, corpus.size());
    return java.util.stream.StreamSupport.stream(corpus.spliterator(), false)
        .map(
            c ->
                DynamicTest.dynamicTest(
                    c.get("value").asText(),
                    () ->
                        assertEquals(
                            c.get("valid").asBoolean(),
                            EmailFormats.generated(c.get("value").asText()),
                            c.toString())));
  }

  @Test
  void independentDeterministicOracle() {
    assertTrue(EmailFormats.deterministic("@"));
    assertFalse(EmailFormats.generated("@"));
    assertFalse(EmailFormats.deterministic("no-at"));
  }
}
