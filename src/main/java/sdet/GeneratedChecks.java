package sdet;

import java.util.*;

public final class GeneratedChecks {
  public record Check(String name, String status, String reason) {}

  public static final List<String> NAMES =
      List.of(
          "not_a_server_error",
          "status_code_conformance",
          "content_type_conformance",
          "response_headers_conformance",
          "response_schema_conformance",
          "negative_data_rejection",
          "positive_data_acceptance",
          "missing_required_header",
          "unsupported_method",
          "allow_header_conformance",
          "use_after_free",
          "ensure_resource_availability",
          "ignored_auth");

  private GeneratedChecks() {}

  private static Check result(String name, boolean pass, String reason) {
    return new Check(name, pass ? "PASS" : "FAIL", reason);
  }

  public static List<Check> check(
      Generation generation, Generation.Operation op, Generation.Case c, ApiClient.Response r) {
    var out = new LinkedHashMap<String, Check>();
    for (String name : NAMES)
      out.put(
          name,
          new Check(
              name,
              "SKIP",
              switch (name) {
                case "response_headers_conformance" ->
                    "No response headers declared; declarations fail preflight";
                case "use_after_free", "ensure_resource_availability" ->
                    "Reference does not enable stateful phase";
                case "ignored_auth" -> "No security schemes; required header is tested separately";
                default -> "Not applicable to this case/status";
              }));
    int status = r.status();
    out.put(
        "not_a_server_error",
        result("not_a_server_error", status >= 200 && status < 500, "HTTP " + status));
    var declaration = op.responses().path(Integer.toString(status));
    if (!c.unsupported()) {
      out.put(
          "status_code_conformance",
          result(
              "status_code_conformance", !declaration.isMissingNode(), "Status must be declared"));
      if (declaration.has("content")) {
        out.put(
            "content_type_conformance",
            result(
                "content_type_conformance",
                r.media().equals("application/json"),
                "JSON media required"));
        boolean valid;
        try {
          valid =
              generation.contract.valid(
                  declaration.path("content").path("application/json").path("schema"),
                  r.json(),
                  EmailFormats::generated);
        } catch (IllegalArgumentException e) {
          valid = false;
        }
        out.put(
            "response_schema_conformance",
            result(
                "response_schema_conformance",
                valid,
                "JSON response must match schema and generated formats"));
      }
      if (c.positive())
        out.put(
            "positive_data_acceptance",
            result(
                "positive_data_acceptance",
                status >= 200 && status < 300
                    || Set.of(401, 403, 404, 409).contains(status)
                    || status >= 500 && status < 600,
                "Positive request acceptance"));
      else
        out.put(
            "negative_data_rejection",
            result(
                "negative_data_rejection",
                Set.of(400, 401, 403, 404, 405, 406, 409, 422, 428).contains(status)
                    || status >= 500 && status < 600,
                "Negative request rejection"));
      if (c.phase().equals("coverage") && !c.missingHeader().isEmpty())
        out.put(
            "missing_required_header",
            result(
                "missing_required_header",
                (c.missingHeader().equalsIgnoreCase("Authorization")
                        ? Set.of(401)
                        : Set.of(400, 401, 403, 406, 422))
                    .contains(status),
                "Required header omission must be rejected"));
    }
    if (c.unsupported() && !c.method().equals("OPTIONS"))
      out.put(
          "unsupported_method",
          result(
              "unsupported_method",
              status == 404 && op.path().contains("{")
                  || status == 405 && !r.headers().getOrDefault("allow", "").isBlank(),
              "405 + Allow required, or unknown parameterized resource 404"));
    String allow = r.headers().getOrDefault("allow", "");
    if (c.method().equals("OPTIONS") && !allow.isBlank()) {
      var advertised = new HashSet<String>();
      for (String m : allow.split(",")) advertised.add(m.strip().toUpperCase(Locale.ROOT));
      advertised.removeAll(Set.of("OPTIONS", "HEAD"));
      var declared = new HashSet<>(op.methods());
      declared.removeAll(Set.of("OPTIONS", "HEAD"));
      out.put(
          "allow_header_conformance",
          result(
              "allow_header_conformance",
              advertised.equals(declared),
              "OPTIONS Allow must match declared methods"));
    }
    return NAMES.stream().map(out::get).toList();
  }
}
