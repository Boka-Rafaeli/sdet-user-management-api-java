package sdet;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

public final class Contract {
  private final JsonNode document;
  private final JsonSchemaFactory factory =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
  private static final Set<String> KEYS =
      Set.of(
          "type",
          "required",
          "properties",
          "items",
          "minimum",
          "maximum",
          "format",
          "example",
          "description",
          "enum",
          "additionalProperties",
          "minLength",
          "maxLength",
          "minItems",
          "maxItems");

  public Contract() throws IOException {
    this(Path.of("openapi/sdet_challenge_api.yml"));
  }

  public Contract(Path path) throws IOException {
    this(new ObjectMapper(new YAMLFactory()).readTree(path.toFile()));
  }

  public Contract(JsonNode document) {
    this.document = document.deepCopy();
    if (!document.path("openapi").asText().startsWith("3.0."))
      throw new IllegalArgumentException("Only OpenAPI 3.0 supported");
    preflight();
  }

  public JsonNode document() {
    return document.deepCopy();
  }

  public JsonNode resolve(JsonNode value) {
    return resolve(value, new HashSet<>());
  }

  private JsonNode resolve(JsonNode value, Set<String> refs) {
    if (value == null || value.isMissingNode())
      throw new IllegalArgumentException("Unresolved schema");
    if (value.has("$ref")) {
      String ref = value.path("$ref").asText();
      if (!ref.startsWith("#/") || !refs.add(ref))
        throw new IllegalArgumentException("External/cyclic reference unsupported");
      var resolved = resolve(document.at(ref.substring(1)), refs);
      refs.remove(ref);
      return resolved;
    }
    if (value.isObject()) {
      var result = Json.MAPPER.createObjectNode();
      value
          .fields()
          .forEachRemaining(
              e -> result.set(e.getKey(), resolve(e.getValue(), new HashSet<>(refs))));
      return result;
    }
    if (value.isArray()) {
      var result = Json.MAPPER.createArrayNode();
      value.forEach(v -> result.add(resolve(v, new HashSet<>(refs))));
      return result;
    }
    return value.deepCopy();
  }

  private void preflight() {
    if (document.has("security") || document.path("components").has("securitySchemes"))
      throw new IllegalArgumentException("Security schemes unsupported");
    document.path("components").path("schemas").forEach(s -> adapt(resolve(s)));
    document
        .path("paths")
        .forEach(
            path ->
                path.fields()
                    .forEachRemaining(
                        e -> {
                          if (e.getKey().equals("parameters")) return;
                          var op = e.getValue();
                          if (op.has("security"))
                            throw new IllegalArgumentException("Security unsupported");
                          op.path("responses")
                              .forEach(
                                  r -> {
                                    if (r.has("headers"))
                                      throw new IllegalArgumentException(
                                          "Response headers unsupported");
                                    validateContent(r);
                                  });
                          if (op.has("requestBody")) validateContent(op.get("requestBody"));
                        }));
  }

  private void validateContent(JsonNode node) {
    node.path("content")
        .fields()
        .forEachRemaining(
            e -> {
              if (!e.getKey().equals("application/json"))
                throw new IllegalArgumentException("Only JSON supported");
              adapt(resolve(e.getValue().path("schema")));
            });
  }

  private JsonNode adapt(JsonNode schema) {
    if (!schema.isObject()) throw new IllegalArgumentException("Schema object required");
    var copy = (ObjectNode) schema.deepCopy();
    schema
        .fieldNames()
        .forEachRemaining(
            k -> {
              if (!KEYS.contains(k))
                throw new IllegalArgumentException("Unsupported schema keyword: " + k);
            });
    if (schema.has("format") && !schema.get("format").asText().equals("email"))
      throw new IllegalArgumentException("Unsupported format");
    copy.remove(List.of("format", "example", "description"));
    if (copy.has("properties")) {
      var props = (ObjectNode) copy.get("properties");
      var names = new ArrayList<String>();
      props.fieldNames().forEachRemaining(names::add);
      for (var name : names) props.set(name, adapt(props.get(name)));
    }
    if (copy.has("items")) copy.set("items", adapt(copy.get("items")));
    return copy;
  }

  public boolean valid(JsonNode schema, JsonNode value) {
    return valid(schema, value, v -> v.contains("@"));
  }

  public boolean valid(JsonNode schema, JsonNode value, Predicate<String> email) {
    var resolved = resolve(schema);
    var adapted = adapt(resolved);
    var config =
        SchemaValidatorsConfig.builder().typeLoose(false).formatAssertionsEnabled(true).build();
    return factory.getSchema(adapted, config).validate(value).isEmpty()
        && formats(resolved, value, email);
  }

  private boolean formats(JsonNode schema, JsonNode value, Predicate<String> email) {
    if (value == null) return true;
    if (schema.path("format").asText().equals("email")
        && value.isTextual()
        && !email.test(value.textValue())) return false;
    if (value.isObject()) {
      var it = schema.path("properties").fields();
      while (it.hasNext()) {
        var e = it.next();
        if (value.has(e.getKey()) && !formats(e.getValue(), value.get(e.getKey()), email))
          return false;
      }
    }
    if (value.isArray() && schema.has("items"))
      for (var item : value) if (!formats(schema.get("items"), item, email)) return false;
    return true;
  }

  public JsonNode response(ApiClient.Response response, String path, String method, int status) {
    return response(response, path, method, status, null);
  }

  public JsonNode response(
      ApiClient.Response response, String path, String method, int status, Trace trace) {
    String operationName = method.toUpperCase(Locale.ROOT) + " " + path;
    check(
        response,
        operationName,
        "status",
        response.status() == status,
        "Unexpected response status",
        trace);
    var operation = document.path("paths").path(path).path(method.toLowerCase(Locale.ROOT));
    check(
        response,
        operationName,
        "operation-declared",
        !operation.isMissingNode(),
        "Undeclared operation",
        trace);
    var entry = operation.path("responses").path(Integer.toString(status));
    check(
        response,
        operationName,
        "response-declared",
        !entry.isMissingNode(),
        "Undeclared status",
        trace);
    if (!entry.has("content")) {
      check(
          response,
          operationName,
          "empty-body",
          response.body().length == 0,
          "Expected empty body",
          trace);
      return NullNode.instance;
    }
    check(
        response,
        operationName,
        "content-type",
        response.media().equals("application/json"),
        "Wrong media type",
        trace);
    JsonNode body;
    try {
      body = response.json();
    } catch (IllegalArgumentException ex) {
      check(response, operationName, "json-body", false, "Expected JSON response", trace);
      throw new AssertionError("Unreachable");
    }
    check(response, operationName, "json-body", true, "Expected JSON response", trace);
    check(
        response,
        operationName,
        "response-schema",
        valid(entry.path("content").path("application/json").path("schema"), body),
        "Response violates OpenAPI schema",
        trace);
    return body;
  }

  private static void check(
      ApiClient.Response response,
      String operation,
      String name,
      boolean passed,
      String message,
      Trace trace) {
    if (trace != null) trace.contract(response, operation, name, passed);
    assertTrue(passed, message);
  }
}
