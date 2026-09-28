package sdet;

import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class Dependencies {
  private Dependencies() {}

  public static Map<String, String> inventory(List<Path> paths) throws Exception {
    var result = new TreeMap<String, String>();
    for (Path p : paths)
      if (p.toString().endsWith(".jar")) {
        String digest =
            HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
        String previous = result.put(p.getFileName().toString(), digest);
        if (previous != null && !previous.equals(digest))
          throw new IllegalStateException("Ambiguous dependency filename");
      }
    if (result.isEmpty()) throw new IllegalStateException("No resolved dependencies");
    return result;
  }

  public static void main(String[] args) throws Exception {
    var loader = (java.net.URLClassLoader) Thread.currentThread().getContextClassLoader();
    var paths = new ArrayList<Path>();
    for (var url : loader.getURLs())
      if (url.getProtocol().equals("file")) paths.add(Path.of(url.toURI()));
    var actual = inventory(paths);
    Path lock = Path.of("docs/dependencies-sha256.json");
    if (args.length == 1 && args[0].equals("record"))
      Files.writeString(
          lock, Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
    else if (!Json.MAPPER.valueToTree(actual).equals(Json.parse(Files.readString(lock))))
      throw new IllegalStateException("Resolved dependencies differ from locked hashes");
    System.out.println("Verified resolved dependency JARs: " + actual.size());
  }
}
