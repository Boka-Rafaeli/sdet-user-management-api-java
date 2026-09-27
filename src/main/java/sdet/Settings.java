package sdet;

import java.net.URI;
import java.time.Duration;
import java.util.*;

public record Settings(
    URI baseUrl,
    String environment,
    String token,
    Duration timeout,
    boolean httpTrace,
    boolean contractTrace,
    boolean baseline) {
  public Settings {
    if (!Set.of("dev", "prod").contains(environment))
      throw new IllegalArgumentException("Expected dev or prod");
    if (!Set.of("http", "https").contains(Objects.toString(baseUrl.getScheme(), ""))
        || baseUrl.getHost() == null
        || baseUrl.getUserInfo() != null
        || baseUrl.getQuery() != null
        || baseUrl.getFragment() != null)
      throw new IllegalArgumentException("Invalid HTTP(S) base URL");
    if (timeout.isZero() || timeout.isNegative())
      throw new IllegalArgumentException("Timeout must be positive");
    Objects.requireNonNull(token);
    baseUrl = URI.create(baseUrl.toString().replaceAll("/+$", ""));
  }

  public static Settings load(Map<String, String> env, String... args) {
    var values = new HashMap<>(env);
    for (int i = 0; i < args.length; i++) {
      String key =
          switch (args[i]) {
            case "--environment" -> "TEST_ENV";
            case "--base-url" -> "BASE_URL";
            case "--timeout" -> "HTTP_TIMEOUT_SECONDS";
            case "--baseline", "--known-bugs-as-xfail" -> "KNOWN_BUGS_AS_XFAIL";
            case "--http-trace" -> "HTTP_TRACE";
            case "--contract-trace" -> "CONTRACT_TRACE";
            default -> throw new IllegalArgumentException("Unknown option");
          };
      if (Set.of("KNOWN_BUGS_AS_XFAIL", "HTTP_TRACE", "CONTRACT_TRACE").contains(key))
        values.put(key, "1");
      else {
        if (++i >= args.length) throw new IllegalArgumentException("Missing option value");
        values.put(key, args[i]);
      }
    }
    double seconds = Double.parseDouble(values.getOrDefault("HTTP_TIMEOUT_SECONDS", "5"));
    if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 86400)
      throw new IllegalArgumentException("Timeout must be finite, positive and <=86400 seconds");
    return new Settings(
        URI.create(values.getOrDefault("BASE_URL", "http://127.0.0.1:3000")),
        values.getOrDefault("TEST_ENV", "dev"),
        values.getOrDefault("AUTH_TOKEN", "mysecrettoken"),
        Duration.ofNanos((long) (seconds * 1e9)),
        "1".equals(values.get("HTTP_TRACE")),
        "1".equals(values.get("CONTRACT_TRACE")),
        "1".equals(values.get("KNOWN_BUGS_AS_XFAIL")));
  }

  public Settings withEnvironment(String value) {
    return new Settings(baseUrl, value, token, timeout, httpTrace, contractTrace, baseline);
  }

  public Settings withBaseUrl(URI value) {
    return new Settings(value, environment, token, timeout, httpTrace, contractTrace, baseline);
  }
}
