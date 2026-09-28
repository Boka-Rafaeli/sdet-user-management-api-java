package sdet;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

public final class Json {
  public static final ObjectMapper MAPPER =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private Json() {}

  public static JsonNode parse(String text) {
    try {
      var value = MAPPER.readTree(text);
      if (value == null || value.isMissingNode())
        throw new IllegalArgumentException("Empty JSON document");
      return value;
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid JSON", e);
    }
  }

  public static String text(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot serialize JSON", e);
    }
  }

  public static ObjectNode object(String text) {
    return (ObjectNode) parse(text);
  }
}
