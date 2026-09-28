package sdet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.*;

public final class Redaction {
  public static final String MASK = "<redacted>";
  private static final Set<String> KEYS =
      Set.of(
          "accesstoken",
          "apikey",
          "authentication",
          "authorization",
          "clientsecret",
          "cookie",
          "credential",
          "credentials",
          "password",
          "privatekey",
          "proxyauthorization",
          "refreshtoken",
          "secret",
          "sessiontoken",
          "setcookie",
          "token",
          "xapikey",
          "xauthtoken");
  private final List<String> secrets;

  public Redaction(String... secrets) {
    var variants = new HashSet<String>();
    for (String s : secrets)
      if (!s.isEmpty()) {
        variants.add(s);
        String encoded = ApiClient.encode(s);
        variants.add(encoded);
        variants.add(encoded.replace("%20", "+"));
        variants.add(encoded.toLowerCase(Locale.ROOT));
      }
    this.secrets =
        variants.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
  }

  public static boolean sensitive(String key) {
    return KEYS.contains(key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
  }

  public String text(String value) {
    for (String secret : secrets) value = value.replace(secret, MASK);
    return value;
  }

  public JsonNode json(JsonNode value) {
    if (value.isTextual()) return TextNode.valueOf(text(value.asText()));
    if (value.isObject()) {
      var out = Json.MAPPER.createObjectNode();
      value
          .fields()
          .forEachRemaining(
              e ->
                  out.set(
                      text(e.getKey()),
                      sensitive(e.getKey()) ? TextNode.valueOf(MASK) : json(e.getValue())));
      return out;
    }
    if (value.isArray()) {
      var out = Json.MAPPER.createArrayNode();
      value.forEach(v -> out.add(json(v)));
      return out;
    }
    return value.deepCopy();
  }

  public String body(String body) {
    try {
      return Json.text(json(Json.parse(body)));
    } catch (IllegalArgumentException e) {
      return text(body);
    }
  }

  public boolean clean(String value) {
    return secrets.stream().noneMatch(value::contains);
  }

  public boolean clean(JsonNode value) {
    if (value.isTextual()) return clean(value.asText());
    if (value.isObject()) {
      var fields = value.fields();
      while (fields.hasNext()) {
        var e = fields.next();
        if (!clean(e.getKey())
            || sensitive(e.getKey()) && !e.getValue().equals(TextNode.valueOf(MASK))
            || !clean(e.getValue())) return false;
      }
    }
    if (value.isArray()) for (var item : value) if (!clean(item)) return false;
    return true;
  }
}
