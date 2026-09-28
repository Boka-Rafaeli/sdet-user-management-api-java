package sdet;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Fail-closed upload boundary. NDJSON sanitization uses per-file atomic replacement. */
public final class Evidence {
  public interface Disk {
    default byte[] read(Path p) throws IOException {
      return Files.readAllBytes(p);
    }

    default void write(Path p, String text) throws IOException {
      Files.writeString(p, text, StandardOpenOption.CREATE_NEW);
    }

    default void move(Path source, Path target) throws IOException {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private Evidence() {}

  static String decode(byte[] bytes) throws CharacterCodingException {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString();
  }

  static List<Path> files(Path root) throws IOException {
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
      throw new IOException("Invalid evidence directory");
    var resolved = root.toRealPath();
    var files = new ArrayList<Path>();
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path p, BasicFileAttributes attrs)
              throws IOException {
            if (Files.isSymbolicLink(p) || !p.toRealPath().startsWith(resolved))
              throw new IOException("Evidence escapes root");
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
            if (!attrs.isRegularFile()
                || attrs.isSymbolicLink()
                || !p.toRealPath().startsWith(resolved))
              throw new IOException("Non-regular evidence");
            files.add(p);
            return FileVisitResult.CONTINUE;
          }
        });
    if (files.isEmpty()) throw new IOException("Empty evidence tree");
    files.sort(Comparator.naturalOrder());
    return files;
  }

  public static void scrub(Path root, Redaction redact) throws IOException {
    scrub(root, redact, new Disk() {});
  }

  public static void scrub(Path root, Redaction redact, Disk disk) throws IOException {
    for (var file : files(root))
      if (file.toString().endsWith(".ndjson")) {
        String text = decode(disk.read(file));
        var output = new StringBuilder();
        for (String line : lines(text)) {
          JsonNode parsed;
          try {
            parsed = Json.parse(line);
          } catch (IllegalArgumentException e) {
            throw new IOException("Malformed NDJSON");
          }
          var safe = redact.json(parsed);
          if (!redact.clean(safe))
            throw new IOException("Sensitive evidence survived sanitization");
          output.append(Json.text(safe)).append('\n');
        }
        Path temporary =
            file.resolveSibling("." + file.getFileName() + "." + UUID.randomUUID() + ".scrubbed");
        boolean created = false;
        try {
          try {
            disk.write(temporary, output.toString());
            created = true;
          } catch (FileAlreadyExistsException e) {
            throw e;
          } catch (IOException e) {
            created = Files.exists(temporary, LinkOption.NOFOLLOW_LINKS);
            throw e;
          }
          disk.move(temporary, file);
          created = false;
        } finally {
          if (created) Files.deleteIfExists(temporary);
        }
      }
  }

  private static List<String> lines(String text) throws IOException {
    if (text.isEmpty()) return List.of();
    var lines = new ArrayList<>(Arrays.asList(text.split("\\r\\n|\\n|\\r", -1)));
    if (lines.getLast().isEmpty()) lines.removeLast();
    for (String line : lines)
      if (line.isBlank() || line.startsWith("\uFEFF"))
        throw new IOException("Blank or BOM NDJSON record");
    return lines;
  }

  public static int verify(Path root, Redaction redact) throws IOException {
    int count = 0;
    for (var file : files(root)) {
      String name = file.getFileName().toString();
      if (!name.matches(".*\\.(html|xml|json|ndjson|log|txt)"))
        throw new IOException("Unsupported evidence format");
      String text = decode(Files.readAllBytes(file));
      if (!redact.clean(text)) throw new IOException("Configured secret in evidence");
      try {
        if (name.endsWith(".json")) {
          if (!redact.clean(Json.parse(text))) throw new IOException("Sensitive JSON field");
        } else if (name.endsWith(".ndjson")) {
          for (String line : lines(text))
            if (!redact.clean(Json.parse(line))) throw new IOException("Sensitive NDJSON field");
        } else if (name.endsWith(".xml")) {
          var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
          factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
          factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
          factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
          factory
              .newDocumentBuilder()
              .parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        } else if (name.endsWith(".html")
            && !text.toLowerCase(Locale.ROOT).contains("<!doctype html>"))
          throw new IOException("Malformed HTML report");
      } catch (IllegalArgumentException
          | javax.xml.parsers.ParserConfigurationException
          | org.xml.sax.SAXException e) {
        throw new IOException("Malformed evidence");
      }
      count++;
    }
    return count;
  }
}
