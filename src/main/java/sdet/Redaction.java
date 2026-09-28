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
    return json(value, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private JsonNode json(JsonNode value, Set<JsonNode> active) {
    if (value.isTextual()) return TextNode.valueOf(text(value.asText()));
    if (!value.isContainerNode()) return value.deepCopy();
    if (!active.add(value)) return TextNode.valueOf("[Circular]");
    try {
      if (value.isObject()) {
        var out = Json.MAPPER.createObjectNode();
        value
            .fields()
            .forEachRemaining(
                e ->
                    out.set(
                        text(e.getKey()),
                        sensitive(e.getKey())
                            ? TextNode.valueOf(MASK)
                            : json(e.getValue(), active)));
        return out;
      }
      var out = Json.MAPPER.createArrayNode();
      value.forEach(v -> out.add(json(v, active)));
      return out;
    } finally {
      active.remove(value);
    }
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
    return clean(value, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private boolean clean(JsonNode value, Set<JsonNode> active) {
    if (value.isTextual()) return clean(value.asText());
    if (!value.isContainerNode()) return true;
    if (!active.add(value)) return false;
    try {
      if (value.isObject()) {
        var fields = value.fields();
        while (fields.hasNext()) {
          var e = fields.next();
          if (!clean(e.getKey())
              || sensitive(e.getKey()) && !e.getValue().equals(TextNode.valueOf(MASK))
              || !clean(e.getValue(), active)) return false;
        }
      } else for (var item : value) if (!clean(item, active)) return false;
      return true;
    } finally {
      active.remove(value);
    }
  }
}
