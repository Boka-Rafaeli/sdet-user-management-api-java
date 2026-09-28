package sdet;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Explicit OpenAPI 3.0 subset. New constraints fail before any request. */
public final class Generation {
  public record Parameter(String name, String in, boolean required, JsonNode schema) {}

  public record Operation(
      String id,
      String path,
      String method,
      List<Parameter> parameters,
      JsonNode body,
      boolean bodyRequired,
      JsonNode responses,
      List<String> methods) {}

  public record Case(
      String operationId,
      String method,
      String path,
      Map<String, String> parameters,
      Map<String, String> headers,
      JsonNode body,
      boolean hasBody,
      boolean positive,
      boolean positivePath,
      String phase,
      String category,
      boolean unsupported,
      String missingHeader) {
    public Case {
      parameters = Map.copyOf(parameters);
      headers = Map.copyOf(headers);
      body = body == null ? NullNode.instance : body.deepCopy();
    }

    public Case withBody(JsonNode value, boolean present, boolean mode, String category) {
      return new Case(
          operationId,
          method,
          path,
          parameters,
          headers,
          value,
          present,
          mode,
          positivePath,
          "coverage",
          category,
          unsupported,
          missingHeader);
    }
  }

  public final Contract contract;

  public Generation(Contract contract) {
    this.contract = contract;
  }

  private static final Set<String> METHODS =
      Set.of("get", "post", "put", "delete", "patch", "head", "options", "trace");

  private static void keys(JsonNode node, Set<String> allowed) {
    if (!node.isObject()) throw new IllegalArgumentException("Expected object");
    node.fieldNames()
        .forEachRemaining(
            k -> {
              if (!allowed.contains(k) && !k.startsWith("x-"))
                throw new IllegalArgumentException("Unsupported OpenAPI keyword: " + k);
            });
  }

  private JsonNode schema(JsonNode input) {
    var s = contract.resolve(input);
    keys(
        s,
        Set.of(
            "type",
            "properties",
            "required",
            "items",
            "minimum",
            "maximum",
            "format",
            "example",
            "description",
            "title",
            "additionalProperties"));
    if (!Set.of("object", "array", "string", "integer", "number", "boolean")
        .contains(s.path("type").asText()))
      throw new IllegalArgumentException("Unsupported schema type");
    if (s.has("additionalProperties") && !s.get("additionalProperties").isBoolean())
      throw new IllegalArgumentException("additionalProperties must be boolean");
    for (String bound : List.of("minimum", "maximum"))
      if (s.has(bound) && !s.get(bound).isNumber())
        throw new IllegalArgumentException("Invalid bound");
    if (s.has("minimum")
        && s.has("maximum")
        && s.get("minimum").doubleValue() > s.get("maximum").doubleValue())
      throw new IllegalArgumentException("Inverted bounds");
    if (s.has("format")
        && (!s.path("type").asText().equals("string")
            || !s.path("format").asText().equals("email")))
      throw new IllegalArgumentException("Unsupported format");
    if (s.has("properties")) s.get("properties").forEach(this::schema);
    for (var r : s.path("required"))
      if (!r.isTextual() || !s.path("properties").has(r.asText()))
        throw new IllegalArgumentException("Invalid required field");
    if (s.has("items")) schema(s.get("items"));
    contract.valid(s, NullNode.instance, EmailFormats::generated);
    return s;
  }

  public List<Operation> operations() {
    var result = new ArrayList<Operation>();
    var paths = contract.document().path("paths").fields();
    while (paths.hasNext()) {
      var path = paths.next();
      var item = path.getValue();
      var allowed = new HashSet<>(METHODS);
      allowed.addAll(Set.of("parameters", "summary", "description"));
      keys(item, allowed);
      var methods = new ArrayList<String>();
      item.fieldNames()
          .forEachRemaining(
              k -> {
                if (METHODS.contains(k)) methods.add(k.toUpperCase(Locale.ROOT));
              });
      for (String method : methods) {
        var op = item.get(method.toLowerCase(Locale.ROOT));
        keys(
            op,
            Set.of(
                "operationId",
                "summary",
                "description",
                "tags",
                "parameters",
                "requestBody",
                "responses",
                "deprecated"));
        var parameters = new LinkedHashMap<String, Parameter>();
        for (JsonNode container : List.of(item, op))
          for (var p : container.path("parameters")) {
            keys(p, Set.of("name", "in", "required", "schema", "description"));
            String location = p.path("in").asText();
            if (!Set.of("path", "header").contains(location))
              throw new IllegalArgumentException("Only path/header parameters supported");
            var ps = schema(p.path("schema"));
            if (!ps.path("type").asText().equals("string"))
              throw new IllegalArgumentException("Only string parameters supported");
            var param =
                new Parameter(
                    p.path("name").asText(), location, p.path("required").asBoolean(), ps);
            parameters.put(location + ":" + param.name(), param);
          }
        if (path.getKey().contains("{")) {
          var matcher = java.util.regex.Pattern.compile("\\{([^}]+)}").matcher(path.getKey());
          while (matcher.find()) {
            var p = parameters.get("path:" + matcher.group(1));
            if (p == null || !p.required())
              throw new IllegalArgumentException("Undeclared path placeholder");
          }
        }
        JsonNode body = null;
        boolean required = false;
        if (op.has("requestBody")) {
          var req = op.get("requestBody");
          keys(req, Set.of("required", "content", "description"));
          body = media(req);
          required = req.path("required").asBoolean();
        }
        var responses = op.path("responses");
        if (responses.isEmpty()) throw new IllegalArgumentException("Missing responses");
        var iter = responses.fields();
        while (iter.hasNext()) {
          var r = iter.next();
          if (!r.getKey().matches("[1-5][0-9]{2}"))
            throw new IllegalArgumentException("Explicit status required");
          keys(r.getValue(), Set.of("description", "content"));
          if (r.getValue().has("content")) media(r.getValue());
        }
        String id = op.path("operationId").asText(method + " " + path.getKey());
        if (result.stream().anyMatch(o -> o.id().equals(id)))
          throw new IllegalArgumentException("Duplicate operation");
        result.add(
            new Operation(
                id,
                path.getKey(),
                method,
                List.copyOf(parameters.values()),
                body,
                required,
                responses.deepCopy(),
                List.copyOf(methods)));
      }
    }
    if (result.isEmpty()) throw new IllegalArgumentException("No operations");
    return List.copyOf(result);
  }

  private JsonNode media(JsonNode owner) {
    var content = owner.path("content");
    if (content.size() != 1 || !content.has("application/json"))
      throw new IllegalArgumentException("Only JSON media supported");
    var media = content.get("application/json");
    keys(media, Set.of("schema"));
    return schema(media.path("schema"));
  }

  public JsonNode example(JsonNode s) {
    JsonNode value;
    if (s.has("example")) value = s.get("example").deepCopy();
    else
      value =
          switch (s.path("type").asText()) {
            case "object" -> {
              var object = Json.MAPPER.createObjectNode();
              s.path("properties")
                  .fields()
                  .forEachRemaining(e -> object.set(e.getKey(), example(e.getValue())));
              yield object;
            }
            case "array" -> Json.MAPPER.createArrayNode();
            case "integer", "number" -> s.has("minimum") ? s.get("minimum") : IntNode.valueOf(0);
            case "boolean" -> BooleanNode.TRUE;
            case "string" ->
                TextNode.valueOf(
                    s.path("format").asText().equals("email")
                        ? "generated@example.com"
                        : "Generated value");
            default -> throw new IllegalArgumentException("Unsupported type");
          };
    if (!contract.valid(s, value, EmailFormats::generated))
      throw new IllegalArgumentException("Invalid documented example");
    return value;
  }

  public Case example(Operation op, String token) {
    var path = new LinkedHashMap<String, String>();
    var headers = new LinkedHashMap<String, String>();
    for (var p : op.parameters())
      (p.in().equals("path") ? path : headers)
          .put(
              p.name(),
              p.name().equalsIgnoreCase("Authentication") ? token : example(p.schema()).asText());
    return new Case(
        op.id(),
        op.method(),
        op.path(),
        path,
        headers,
        op.body() == null ? null : example(op.body()),
        op.body() != null,
        true,
        true,
        "examples",
        "schema-example",
        false,
        "");
  }

  public boolean valid(Operation op, Case c) {
    if (c.unsupported()) return false;
    if (op.body() != null
        && (!c.hasBody()
            ? op.bodyRequired()
            : !contract.valid(op.body(), c.body(), EmailFormats::generated))) return false;
    for (var p : op.parameters()) {
      var source = p.in().equals("path") ? c.parameters() : c.headers();
      String value =
          source.entrySet().stream()
              .filter(
                  e ->
                      p.in().equals("header")
                          ? e.getKey().equalsIgnoreCase(p.name())
                          : e.getKey().equals(p.name()))
              .map(Map.Entry::getValue)
              .findFirst()
              .orElse(null);
      if (value == null
          ? p.required()
          : !contract.valid(p.schema(), TextNode.valueOf(value), EmailFormats::generated))
        return false;
    }
    return true;
  }

  public List<Case> coverage(Operation op, String token) {
    var base = example(op, token);
    var cases = new ArrayList<Case>();
    if (op.body() != null) {
      for (String value : List.of("null", "[]", "\"\"", "42", "true"))
        if (!contract.valid(op.body(), Json.parse(value), EmailFormats::generated))
          cases.add(base.withBody(Json.parse(value), true, false, "body-type-" + value));
      if (op.bodyRequired()) cases.add(base.withBody(null, false, false, "missing-body"));
      for (var r : op.body().path("required")) {
        var body = (ObjectNode) base.body().deepCopy();
        body.remove(r.asText());
        cases.add(base.withBody(body, true, false, "missing-" + r.asText()));
      }
      var fields = op.body().path("properties").fields();
      while (fields.hasNext()) {
        var field = fields.next();
        var candidates = new ArrayList<JsonNode>();
        for (String value : List.of("null", "true", "false", "0", "1.5", "\"\"", "[]", "{}"))
          candidates.add(Json.parse(value));
        for (String bound : List.of("minimum", "maximum"))
          if (field.getValue().has(bound)) {
            double v = field.getValue().get(bound).doubleValue();
            for (double n : List.of(v - 1, v, v + 1))
              candidates.add(
                  n == Math.rint(n) ? LongNode.valueOf((long) n) : DoubleNode.valueOf(n));
          }
        if (field.getValue().path("type").asText().equals("string"))
          for (String s :
              List.of("Å user", "not-an-email", "user+tag@example.com", "user%tag@example.com"))
            candidates.add(TextNode.valueOf(s));
        int i = 0;
        for (var value : candidates) {
          var body = (ObjectNode) base.body().deepCopy();
          body.set(field.getKey(), value);
          cases.add(
              base.withBody(
                  body,
                  true,
                  contract.valid(field.getValue(), value, EmailFormats::generated),
                  field.getKey() + "-" + (i++)));
        }
      }
      if (op.body().path("additionalProperties").asBoolean(true) && base.body().isObject()) {
        var body = (ObjectNode) base.body().deepCopy();
        body.put("extra", "allowed");
        cases.add(base.withBody(body, true, true, "additional-property"));
      }
    }
    for (var p : op.parameters()) {
      if (p.in().equals("header") && p.required()) {
        var headers = new HashMap<>(base.headers());
        headers.remove(p.name());
        cases.add(
            new Case(
                op.id(),
                op.method(),
                op.path(),
                base.parameters(),
                headers,
                base.body(),
                base.hasBody(),
                false,
                true,
                "coverage",
                "missing-header-" + p.name(),
                false,
                p.name()));
      }
      if (p.in().equals("path") && p.schema().path("format").asText().equals("email")) {
        var parameters = new HashMap<>(base.parameters());
        parameters.put(p.name(), "invalid-email");
        cases.add(
            new Case(
                op.id(),
                op.method(),
                op.path(),
                parameters,
                base.headers(),
                base.body(),
                base.hasBody(),
                false,
                false,
                "coverage",
                "invalid-path",
                false,
                ""));
      }
    }
    if (op.method().equals(op.methods().getFirst()))
      for (String method :
          List.of("GET", "PUT", "POST", "DELETE", "OPTIONS", "PATCH", "TRACE", "QUERY"))
        if (!op.methods().contains(method))
          cases.add(
              new Case(
                  op.id(),
                  method,
                  op.path(),
                  base.parameters(),
                  base.headers(),
                  null,
                  false,
                  false,
                  true,
                  "coverage",
                  "unsupported-" + method,
                  true,
                  ""));
    for (var c : cases)
      if (!c.unsupported() && valid(op, c) != c.positive())
        throw new IllegalStateException("Incorrect generated case mode: " + c.category());
    return cases;
  }
}
