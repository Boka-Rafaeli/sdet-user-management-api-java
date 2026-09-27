package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SettingsTest {
  @Test
  void precedenceAndIndependentEnvironments() {
    var dev =
        Settings.load(
            Map.of("TEST_ENV", "prod", "HTTP_TIMEOUT_SECONDS", "9"),
            "--environment",
            "dev",
            "--timeout",
            "2");
    assertEquals("dev", dev.environment());
    assertEquals(2000, dev.timeout().toMillis());
    assertEquals("prod", dev.withEnvironment("prod").environment());
    assertEquals("dev", dev.environment());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "NaN", "Infinity", "bad", "1e99"})
  void invalidTimeout(String value) {
    assertThrows(
        IllegalArgumentException.class, () -> Settings.load(Map.of("HTTP_TIMEOUT_SECONDS", value)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "garbage",
        "ftp://a",
        "http://user:pass@a",
        "http://a?x=y",
        "http://a#b",
        "http:/"
      })
  void invalidUrl(String value) {
    assertThrows(IllegalArgumentException.class, () -> Settings.load(Map.of("BASE_URL", value)));
  }

  @Test
  void invalidCli() {
    assertThrows(IllegalArgumentException.class, () -> Settings.load(Map.of(), "--unknown"));
    assertThrows(IllegalArgumentException.class, () -> Settings.load(Map.of(), "--environment"));
    assertThrows(IllegalArgumentException.class, () -> Settings.load(Map.of("TEST_ENV", "stage")));
  }
}
