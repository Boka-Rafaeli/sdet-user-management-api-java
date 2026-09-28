package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class EvidenceTest {
  @Test
  void atomicScrubAndIndependentRetainedGate() throws Exception {
    var root = Files.createTempDirectory("evidence");
    var f = root.resolve("events.ndjson");
    Files.writeString(f, "{\"password\":\"unknown\",\"url\":\"a%2Bb\"}\n");
    var redaction = new Redaction("a+b");
    assertThrows(IOException.class, () -> Evidence.verify(root, redaction));
    Evidence.scrub(root, redaction);
    assertEquals(1, Evidence.verify(root, redaction));
    assertFalse(Files.readString(f).contains("unknown"));
    Files.writeString(root.resolve("summary.json"), "{\"Authentication\":\"other\"}");
    assertThrows(IOException.class, () -> Evidence.verify(root, redaction));
  }

  @Test
  void malformedUtf8BlankBomAndSymlinksFailClosed() throws Exception {
    for (byte[] value :
        new byte[][] {
          "{bad}".getBytes(),
          "{}\n\n".getBytes(),
          "\uFEFF{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
          {(byte) 0xc3, (byte) 0x28}
        }) {
      var root = Files.createTempDirectory("bad-evidence");
      var file = root.resolve("events.ndjson");
      Files.write(file, value);
      assertThrows(IOException.class, () -> Evidence.scrub(root, new Redaction("secret")));
      assertArrayEquals(value, Files.readAllBytes(file));
    }
    var root = Files.createTempDirectory("linked-evidence");
    Files.createSymbolicLink(root.resolve("leak"), Files.createTempFile("secret", ".json"));
    assertThrows(IOException.class, () -> Evidence.verify(root, new Redaction()));
  }

  @Test
  void readWriteRenameFailuresPreserveOriginal() throws Exception {
    for (String fault : new String[] {"read", "write", "rename"}) {
      var root = Files.createTempDirectory("atomic-evidence");
      var file = root.resolve("events.ndjson");
      String original = "{\"token\":\"secret\"}\n";
      Files.writeString(file, original);
      var disk =
          new Evidence.Disk() {
            @Override
            public byte[] read(Path p) throws IOException {
              if (fault.equals("read")) throw new IOException("injected");
              return Evidence.Disk.super.read(p);
            }

            @Override
            public void write(Path p, String s) throws IOException {
              if (fault.equals("write")) throw new IOException("injected");
              Evidence.Disk.super.write(p, s);
            }

            @Override
            public void move(Path p, Path q) throws IOException {
              throw new IOException("injected");
            }
          };
      assertThrows(IOException.class, () -> Evidence.scrub(root, new Redaction("secret"), disk));
      assertEquals(original, Files.readString(file));
      try (var files = Files.list(root)) {
        assertEquals(1, files.count());
      }
    }
  }

  @Test
  void everyRetainedFormatIsGuarded() throws Exception {
    for (String suffix : new String[] {"html", "xml", "json", "ndjson", "txt", "log", "bin"}) {
      var root = Files.createTempDirectory("retained");
      Files.writeString(root.resolve("evidence." + suffix), "secret");
      assertThrows(IOException.class, () -> Evidence.verify(root, new Redaction("secret")));
    }
    var empty = Files.createTempDirectory("empty");
    assertThrows(IOException.class, () -> Evidence.verify(empty, new Redaction()));
  }
}
