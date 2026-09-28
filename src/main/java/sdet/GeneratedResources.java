package sdet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.*;

public final class GeneratedResources implements AutoCloseable {
  private final ApiClient client;
  private final String email;
  private final Set<String> owned = new TreeSet<>();
  private boolean closed;

  public GeneratedResources(ApiClient client, String email) {
    this.client = client;
    this.email = email;
    owned.add(email);
  }

  public void prepare() throws Exception {
    if (closed) throw new IllegalStateException("Seeder closed");
    var reset = client.delete(email);
    if (!Set.of(204, 404).contains(reset.status())) throw new IOException("Resource reset failed");
    var payload =
        Json.MAPPER
            .createObjectNode()
            .put("name", "Generated Resource")
            .put("email", email)
            .put("age", 42);
    var created = client.create(payload);
    if (created.status() != 201 || !created.json().equals(payload))
      throw new IOException("Resource create failed");
    var read = client.get(email);
    if (read.status() != 200 || !read.json().equals(payload))
      throw new IOException("Resource verification failed");
  }

  public void track(JsonNode body) {
    if (body.isObject()
        && body.hasNonNull("email")
        && (body.get("email").isTextual() || body.get("email").isNumber()))
      owned.add(body.get("email").asText());
  }

  public Generation.Case prepare(Generation.Case input) throws Exception {
    if (closed) throw new IllegalStateException("Seeder closed");
    if (!Set.of("PUT", "DELETE").contains(input.method())
        || !input.positivePath()
        || !input.parameters().containsKey("email")) {
      track(input.body());
      return input;
    }
    prepare();
    var parameters = new HashMap<>(input.parameters());
    parameters.put("email", email);
    JsonNode body = input.body().deepCopy();
    if (input.method().equals("PUT") && input.positive() && body.isObject())
      ((ObjectNode) body).put("email", email);
    else track(body);
    return new Generation.Case(
        input.operationId(),
        input.method(),
        input.path(),
        parameters,
        input.headers(),
        body,
        input.hasBody(),
        input.positive(),
        input.positivePath(),
        input.phase(),
        input.category(),
        input.unsupported(),
        input.missingHeader());
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    for (String key : owned)
      try {
        client.delete(key);
      } catch (IOException ignored) {
        /* Reference permits best-effort cleanup of generated resources. */
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Resource cleanup interrupted", e);
      }
  }
}
