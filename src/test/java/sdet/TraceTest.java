package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class TraceTest {
  @Test
  void nestedKeysRawAndEncodedSecretsAreRemoved() {
    var r = new Redaction("a+b /%");
    var input =
        Json.parse(
            "{\"nest\":[{\"Authorization\":\"other-secret\",\"password\":{\"x\":42}}],\"safe\":\"a+b /%\"}");
    var original = input.deepCopy();
    var sanitized = r.json(input);
    assertTrue(r.clean(sanitized));
    assertFalse(sanitized.toString().contains("other-secret"));
    assertEquals(original, input);
    assertFalse(r.clean(input));
    assertEquals(Redaction.MASK, r.text(ApiClient.encode("a+b /%")));
    assertTrue(new Redaction("").clean("anything"));
  }

  @Test
  void independentFlagsAndIds() {
    var output = new ArrayList<String>();
    var settings = Settings.load(Map.of("AUTH_TOKEN", "secret"), "--http-trace");
    var trace = new Trace(settings, output::add);
    var request =
        new ApiClient.Request(
            "dev-4",
            "GET",
            URI.create("http://localhost/dev/users?token=unknown"),
            Map.of("Authentication", "secret"),
            "{\"password\":\"private\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            Duration.ofSeconds(1));
    trace.request(request);
    var response =
        new ApiClient.Response(
            200, Map.of(), "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), request);
    trace.response(response);
    trace.error(new java.io.IOException("secret"));
    trace.contract(response, "GET /users", true);
    assertEquals(3, output.size());
    assertTrue(output.getFirst().contains("dev-4"));
    assertFalse(String.join("", output).contains("secret"));
    assertFalse(String.join("", output).contains("private"));
    assertFalse(String.join("", output).contains("unknown"));
    output.clear();
    var onlyContract = new Trace(Settings.load(Map.of(), "--contract-trace"), output::add);
    onlyContract.request(request);
    onlyContract.contract(response, "GET /users", true);
    assertEquals(1, output.size());
    assertTrue(output.getFirst().startsWith("CONTRACT PASS"));
  }
}
