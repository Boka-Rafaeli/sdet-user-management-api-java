package sdet;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class Trace {
  private final boolean http, contract;
  private final Redaction redact;
  private final Consumer<String> sink;

  public Trace(Settings settings, Consumer<String> sink) {
    http = settings.httpTrace();
    contract = settings.contractTrace();
    redact = new Redaction(settings.token());
    this.sink = sink;
  }

  public void request(ApiClient.Request r) {
    if (http)
      sink.accept(
          "HTTP request id="
              + r.id()
              + " method="
              + r.method()
              + " url="
              + url(r.uri().toString())
              + " headers="
              + redact.body(Json.text(r.headers()))
              + " body="
              + redact.body(
                  r.body() == null ? "<empty>" : new String(r.body(), StandardCharsets.UTF_8)));
  }

  public void response(ApiClient.Response r) {
    if (http)
      sink.accept(
          "HTTP response id="
              + r.request().id()
              + " status="
              + r.status()
              + " body="
              + redact.body(r.text()));
  }

  public void error(Exception e) {
    if (http) sink.accept("HTTP error " + redact.text(e.toString()));
  }

  public void contract(ApiClient.Response r, String operation, boolean passed) {
    if (contract)
      sink.accept(
          "CONTRACT "
              + (passed ? "PASS" : "FAIL")
              + " operation="
              + operation
              + " request="
              + (r.request() == null ? "-" : r.request().id()));
  }

  private String url(String value) {
    int q = value.indexOf('?');
    if (q < 0) return redact.text(value);
    String prefix = value.substring(0, q + 1);
    var pairs = value.substring(q + 1).split("&");
    for (int i = 0; i < pairs.length; i++) {
      String key = pairs[i].split("=", 2)[0];
      if (Redaction.sensitive(java.net.URLDecoder.decode(key, StandardCharsets.UTF_8)))
        pairs[i] = key + "=" + ApiClient.encode(Redaction.MASK);
    }
    return redact.text(prefix + String.join("&", pairs));
  }
}
