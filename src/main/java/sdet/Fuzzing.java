package sdet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import net.jqwik.api.*;

public final class Fuzzing {
  public static final String VERSION = "java-generator-1/jqwik-1.9.3";

  @FunctionalInterface
  public interface Predicate<T> {
    boolean test(T value) throws Exception;
  }

  public record Minimal<T>(T value, int attempts, boolean exhausted) {}

  private Fuzzing() {}

  public static Arbitrary<JsonNode> values(JsonNode schema) {
    return switch (schema.path("type").asText()) {
      case "integer" ->
          Arbitraries.integers()
              .between(schema.path("minimum").asInt(-100), schema.path("maximum").asInt(200))
              .map(IntNode::valueOf);
      case "number" ->
          Arbitraries.doubles()
              .between(schema.path("minimum").asDouble(-100), schema.path("maximum").asDouble(200))
              .map(DoubleNode::valueOf);
      case "boolean" -> Arbitraries.of(true, false).map(BooleanNode::valueOf);
      case "string" ->
          schema.path("format").asText().equals("email")
              ? Arbitraries.strings()
                  .alpha()
                  .ofMinLength(1)
                  .ofMaxLength(20)
                  .map(v -> TextNode.valueOf(v + "@example.com"))
              : Arbitraries.strings()
                  .withChars('a', 'b', 'Z', ' ', 'Å', '<', '&')
                  .ofMinLength(0)
                  .ofMaxLength(30)
                  .map(TextNode::valueOf);
      case "array" ->
          values(schema.path("items"))
              .list()
              .ofMaxSize(3)
              .map(
                  items -> {
                    var a = Json.MAPPER.createArrayNode();
                    items.forEach(a::add);
                    return a;
                  });
      case "object" -> {
        Arbitrary<JsonNode> arbitrary = Arbitraries.just(Json.MAPPER.createObjectNode());
        var fields = schema.path("properties").fields();
        while (fields.hasNext()) {
          var field = fields.next();
          arbitrary =
              Combinators.combine(arbitrary, values(field.getValue()))
                  .as(
                      (a, v) -> {
                        var copy = (ObjectNode) a.deepCopy();
                        copy.set(field.getKey(), v);
                        return copy;
                      });
        }
        yield arbitrary;
      }
      default -> throw new IllegalArgumentException("Unsupported fuzz schema");
    };
  }

  public static List<Shrinkable<Generation.Case>> cases(
      Generation g, Generation.Operation op, String token, long seed, int count) {
    if (count < 1 || count > 10000) throw new IllegalArgumentException("Invalid example limit");
    if (!net.jqwik.api.sessions.JqwikSession.isActive())
      throw new IllegalStateException(
          "Generation requires an owned jqwik session through consumption and shrinking");
    var base = g.example(op, token);
    Arbitrary<Generation.Case> arbitrary;
    if (op.body() != null) {
      var boundaries =
          g.coverage(op, token).stream()
              .filter(c -> c.hasBody() && !c.unsupported())
              .map(Generation.Case::body)
              .toList();
      arbitrary =
          Arbitraries.oneOf(values(op.body()), Arbitraries.of(boundaries))
              .map(
                  body ->
                      new Generation.Case(
                          op.id(),
                          op.method(),
                          op.path(),
                          base.parameters(),
                          base.headers(),
                          body,
                          true,
                          g.contract.valid(op.body(), body, EmailFormats::generated),
                          true,
                          "fuzzing",
                          "jqwik-body",
                          false,
                          ""));
    } else {
      var addresses =
          Arbitraries.oneOf(
              Arbitraries.strings()
                  .alpha()
                  .ofMinLength(1)
                  .ofMaxLength(20)
                  .map(value -> value + "@example.com"),
              Arbitraries.of("invalid-email", "@", "a@-b"));
      arbitrary =
          addresses.map(
              value -> {
                var parameters = new HashMap<>(base.parameters());
                boolean positive = true;
                if (parameters.containsKey("email")) {
                  parameters.put("email", value);
                  positive = EmailFormats.generated(value);
                }
                return new Generation.Case(
                    op.id(),
                    op.method(),
                    op.path(),
                    parameters,
                    base.headers(),
                    base.body(),
                    base.hasBody(),
                    positive,
                    positive,
                    "fuzzing",
                    "jqwik-path",
                    false,
                    "");
              });
    }
    var generator = arbitrary.generator(100);
    var random = new Random(seed);
    var result = new ArrayList<Shrinkable<Generation.Case>>();
    for (int i = 0; i < count; i++) result.add(generator.next(random));
    return result;
  }

  public static <T> Minimal<T> minimize(Shrinkable<T> original, int limit, Predicate<T> reproduces)
      throws Exception {
    boolean owned = !net.jqwik.api.sessions.JqwikSession.isActive();
    if (owned) net.jqwik.api.sessions.JqwikSession.start();
    try {
      return minimizeInternal(original, limit, reproduces);
    } finally {
      if (owned) net.jqwik.api.sessions.JqwikSession.finish();
    }
  }

  private static <T> Minimal<T> minimizeInternal(
      Shrinkable<T> original, int limit, Predicate<T> reproduces) throws Exception {
    if (limit < 0) throw new IllegalArgumentException("Negative shrink limit");
    var current = original;
    int attempts = 0;
    boolean progressed = true;
    while (progressed && attempts < limit) {
      progressed = false;
      try (var stream = current.shrink()) {
        var iterator = stream.iterator();
        while (iterator.hasNext() && attempts < limit) {
          var candidate = iterator.next();
          attempts++;
          if (reproduces.test(candidate.value())) {
            current = candidate;
            progressed = true;
            break;
          }
        }
      }
    }
    return new Minimal<>(current.value(), attempts, attempts == limit);
  }

  public static Generation.Case replay(JsonNode serialized, String token) {
    try {
      var c = Json.MAPPER.treeToValue(serialized, Generation.Case.class);
      var headers = new HashMap<>(c.headers());
      headers.replaceAll((k, v) -> k.equalsIgnoreCase("Authentication") ? token : v);
      return new Generation.Case(
          c.operationId(),
          c.method(),
          c.path(),
          c.parameters(),
          headers,
          c.body(),
          c.hasBody(),
          c.positive(),
          c.positivePath(),
          c.phase(),
          c.category(),
          c.unsupported(),
          c.missingHeader());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid replay request", e);
    }
  }
}
