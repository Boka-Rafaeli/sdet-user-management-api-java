package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class SignalTest {
  public static class Probe {
    public static void main(String[] args) throws Exception {
      try (var runtime = DockerRuntime.start()) {
        Files.writeString(Path.of(args[0]), runtime.id());
        new java.util.concurrent.CountDownLatch(1).await();
      }
    }
  }

  @Test
  void sigtermCleansOwnedContainer() throws Exception {
    var dir = Files.createTempDirectory("signal-probe");
    var idFile = dir.resolve("id");
    var process =
        JvmSupport.process(Probe.class, idFile.toString())
            .redirectErrorStream(true)
            .redirectOutput(dir.resolve("process.log").toFile())
            .start();
    try {
      long deadline = System.nanoTime() + Duration.ofSeconds(50).toNanos();
      while (!Files.exists(idFile) && process.isAlive() && System.nanoTime() < deadline)
        Thread.sleep(25);
      assertTrue(Files.exists(idFile));
      String id = Files.readString(idFile);
      process.destroy();
      assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS));
      assertThrows(java.io.IOException.class, () -> DockerRuntime.execute(List.of("inspect", id)));
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        if (Files.exists(idFile))
          DockerRuntime.execute(List.of("rm", "--force", Files.readString(idFile)));
      }
    }
  }
}
