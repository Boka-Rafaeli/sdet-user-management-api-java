package sdet;

import java.nio.file.Path;
import java.util.*;

public record Options(
    Settings settings,
    Exploration.Limits limits,
    Path schema,
    Path replay,
    int replayIndex,
    boolean external) {
  public static Options parse(Map<String, String> env, String... args) {
    var settingsArgs = new ArrayList<String>();
    long seed = 424242;
    int examples = 20, failures = 20, shrink = 30, records = 2000, index = 0;
    Path schema = Path.of("openapi/sdet_challenge_api.yml"), replay = null;
    boolean external = env.containsKey("BASE_URL");
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (Set.of(
              "--seed",
              "--examples",
              "--max-failures",
              "--shrink-limit",
              "--record-limit",
              "--schema",
              "--replay",
              "--replay-index")
          .contains(arg)) {
        if (++i >= args.length) throw new IllegalArgumentException("Missing generator option");
        String v = args[i];
        switch (arg) {
          case "--seed" -> seed = Long.parseLong(v);
          case "--examples" -> examples = Integer.parseInt(v);
          case "--max-failures" -> failures = Integer.parseInt(v);
          case "--shrink-limit" -> shrink = Integer.parseInt(v);
          case "--record-limit" -> records = Integer.parseInt(v);
          case "--schema" -> schema = Path.of(v);
          case "--replay" -> replay = Path.of(v);
          case "--replay-index" -> index = Integer.parseInt(v);
          default -> throw new IllegalArgumentException("Unknown option");
        }
      } else {
        settingsArgs.add(arg);
        if (arg.equals("--base-url")) external = true;
      }
    }
    if (index < 0) throw new IllegalArgumentException("Replay index must be nonnegative");
    return new Options(
        Settings.load(env, settingsArgs.toArray(String[]::new)),
        new Exploration.Limits(seed, examples, failures, shrink, records),
        schema,
        replay,
        index,
        external);
  }
}
