package sdet;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class ApiClient implements AutoCloseable {
  public record Request(
      String id,
      String method,
      URI uri,
      Map<String, String> headers,
      byte[] body,
      Duration timeout) {
    public Request {
      headers = Map.copyOf(headers);
      body = body == null ? null : body.clone();
    }

    @Override
    public byte[] body() {
      return body == null ? null : body.clone();
    }
  }

  public record Response(int status, Map<String, String> headers, byte[] body, Request request) {
    public Response {
      headers = Map.copyOf(headers);
      body = body.clone();
    }

    @Override
    public byte[] body() {
      return body.clone();
    }

    public String text() {
      return new String(body, StandardCharsets.UTF_8);
    }

    public JsonNode json() {
      return Json.parse(text());
    }

    public String media() {
      return headers.getOrDefault("content-type", "").split(";")[0].trim().toLowerCase(Locale.ROOT);
    }
  }

  @FunctionalInterface
  public interface Transport {
    Response send(Request request) throws IOException, InterruptedException;
  }

  private final Settings settings;
  private final HttpClient http;
  private final Transport transport;
  private int sequence;
  private boolean closed;

  public ApiClient(Settings settings) {
    this(settings, null);
  }

  public ApiClient(Settings settings, Transport transport) {
    this.settings = settings;
    http =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(settings.timeout())
            .build();
    this.transport = transport == null ? this::send : transport;
  }

  public Settings settings() {
    return settings;
  }

  public Response list() throws IOException, InterruptedException {
    return raw("GET", "/users", null, Map.of());
  }

  public Response create(JsonNode body) throws IOException, InterruptedException {
    return json("POST", "/users", body);
  }

  public Response get(String email) throws IOException, InterruptedException {
    return raw("GET", "/users/" + encode(email), null, Map.of());
  }

  public Response update(String email, JsonNode body) throws IOException, InterruptedException {
    return json("PUT", "/users/" + encode(email), body);
  }

  public Response delete(String email) throws IOException, InterruptedException {
    return delete(email, settings.token());
  }

  public Response delete(String email, String token) throws IOException, InterruptedException {
    return raw(
        "DELETE",
        "/users/" + encode(email),
        null,
        token == null ? Map.of() : Map.of("Authentication", token));
  }

  public Response json(String method, String path, JsonNode body)
      throws IOException, InterruptedException {
    return raw(
        method,
        path,
        Json.text(body).getBytes(StandardCharsets.UTF_8),
        Map.of("Content-Type", "application/json"));
  }

  public Response raw(String method, String path, byte[] body, Map<String, String> headers)
      throws IOException, InterruptedException {
    if (closed) throw new IllegalStateException("API client closed");
    var normalized = new TreeMap<String, String>();
    normalized.put("accept", "application/json");
    headers.forEach((k, v) -> normalized.put(k.toLowerCase(Locale.ROOT), v));
    var request =
        new Request(
            settings.environment() + "-" + (++sequence),
            method.toUpperCase(Locale.ROOT),
            URI.create(
                settings.baseUrl()
                    + "/"
                    + settings.environment()
                    + "/"
                    + path.replaceFirst("^/+", "")),
            normalized,
            body,
            settings.timeout());
    var trace = new Trace(settings, System.out::println);
    trace.request(request);
    try {
      var response = transport.send(request);
      trace.response(response);
      return response;
    } catch (IOException | InterruptedException error) {
      trace.error(error);
      throw error;
    }
  }

  private Response send(Request request) throws IOException, InterruptedException {
    var builder =
        HttpRequest.newBuilder(request.uri())
            .timeout(request.timeout())
            .method(
                request.method(),
                request.body() == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(request.body()));
    request.headers().forEach(builder::header);
    var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    var headers = new HashMap<String, String>();
    response.headers().map().forEach((k, v) -> headers.put(k, String.join(", ", v)));
    return new Response(response.statusCode(), headers, response.body(), request);
  }

  public static String encode(String value) {
    var result = new StringBuilder();
    for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
      int c = b & 255;
      if (c >= 'a' && c <= 'z'
          || c >= 'A' && c <= 'Z'
          || c >= '0' && c <= '9'
          || "-._~".indexOf(c) >= 0) result.append((char) c);
      else result.append(String.format("%%%02X", c));
    }
    return result.toString();
  }

  @Override
  public void close() {
    closed = true;
    http.close();
  }
}
