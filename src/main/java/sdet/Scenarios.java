package sdet;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

public final class Scenarios {
  @FunctionalInterface
  public interface Body {
    void run(Context context) throws Exception;
  }

  public static final class Context {
    public final ApiClient client, dev, prod;
    public final Contract contract;
    public final OwnedUsers owned;
    public final Settings settings;
    private final String bug;
    private final Set<String> affected;

    Context(
        Settings settings,
        ApiClient dev,
        ApiClient prod,
        Contract contract,
        String bug,
        Set<String> affected) {
      this.settings = settings;
      this.dev = dev;
      this.prod = prod;
      this.contract = contract;
      client = settings.environment().equals("dev") ? dev : prod;
      owned = new OwnedUsers((env, email) -> (env.equals("dev") ? dev : prod).delete(email));
      this.bug = bug;
      this.affected = affected;
    }

    public ObjectNode user() {
      return OwnedUsers.user(settings.environment());
    }

    public void own(String email) {
      owned.add(settings.environment(), email);
    }

    public JsonNode response(ApiClient.Response r, String path, String method, int status) {
      var trace = new Trace(settings, System.out::println);
      try {
        var body = contract.response(r, path, method, status);
        trace.contract(r, method + " " + path, true);
        return body;
      } catch (AssertionError error) {
        trace.contract(r, method + " " + path, false);
        throw error;
      }
    }

    public ObjectNode create() throws Exception {
      return create(user());
    }

    public ObjectNode create(ObjectNode payload) throws Exception {
      own(payload.path("email").asText());
      assertEquals(payload, response(client.create(payload), "/users", "post", 201));
      return payload;
    }

    public JsonNode read(String email) throws Exception {
      return response(client.get(email), "/users/{email}", "get", 200);
    }

    public JsonNode list() throws Exception {
      return response(client.list(), "/users", "get", 200);
    }

    public void unchanged(JsonNode payload) throws Exception {
      assertEquals(payload, read(payload.path("email").asText()));
    }

    public void check(Baseline.Action expected, Baseline.Action signature) throws Exception {
      Baseline.check(settings, bug, affected, expected, signature);
    }
  }

  public static final class Builder {
    final Settings settings;
    final ApiClient dev, prod;
    final Contract contract;
    public final List<SuiteRunner.Case> cases = new ArrayList<>();

    Builder(Settings settings, ApiClient dev, ApiClient prod, Contract contract) {
      this.settings = settings;
      this.dev = dev;
      this.prod = prod;
      this.contract = contract;
    }

    public void add(String id, Body body) {
      add(id, null, Set.of("dev", "prod"), body);
    }

    public void add(String id, String bug, Body body) {
      add(id, bug, Set.of("dev", "prod"), body);
    }

    public void add(String id, String bug, Set<String> affected, Body body) {
      var c = new Context(settings, dev, prod, contract, bug, affected);
      cases.add(new SuiteRunner.Case(id, () -> body.run(c), c.owned::cleanup));
    }
  }

  public static List<SuiteRunner.Case> build(
      String scope, Settings settings, ApiClient dev, ApiClient prod, Contract contract) {
    var b = new Builder(settings, dev, prod, contract);
    if (scope.equals("isolation")) {
      Isolation.add(b);
      return b.cases;
    }
    Crud.add(b);
    Updates.add(b);
    Validation.post(b);
    PutValidation.add(b);
    Protocol.add(b);
    Authentication.add(b);
    return b.cases;
  }

  public static void absent(JsonNode users, String email) {
    for (var user : users)
      assertNotEquals(email, user.path("email").asText(), "Unexpected persisted user");
  }

  public static void contains(JsonNode users, JsonNode expected) {
    var matches = new ArrayList<JsonNode>();
    for (var user : users) if (user.path("email").equals(expected.path("email"))) matches.add(user);
    assertEquals(List.of(expected), matches);
  }

  public static void internalError(ApiClient.Response r) {
    assertEquals(500, r.status());
    assertEquals("application/json", r.media());
    assertEquals(Json.parse("{\"error\":\"Internal server error\"}"), r.json());
  }

  private Scenarios() {}
}
