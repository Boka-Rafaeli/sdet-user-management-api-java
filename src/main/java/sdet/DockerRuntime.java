package sdet;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class DockerRuntime implements AutoCloseable {
  public static final String IMAGE =
      "ghcr.io/danielsilva-loanpro/sdet-interview-challenge@sha256:c80c42ffafccb6ba9cd9a128421445d308225f09902c03aeadbc99e321176bbc";

  @FunctionalInterface
  public interface Command {
    String run(List<String> args) throws IOException;
  }

  private final Command command;
  private final String id;
  private final URI baseUrl;
  private final String digest;
  private boolean stopped;
  private final Thread shutdown;

  private DockerRuntime(Command command, String id, URI baseUrl, String digest) {
    this.command = command;
    this.id = id;
    this.baseUrl = baseUrl;
    this.digest = digest;
    shutdown =
        new Thread(
            () -> {
              try {
                close();
              } catch (IOException e) {
                System.err.println("Owned container cleanup failed: " + id);
              }
            },
            "sdet-container-cleanup");
    Runtime.getRuntime().addShutdownHook(shutdown);
  }

  public static DockerRuntime start() throws IOException {
    return start(DockerRuntime::execute, true);
  }

  static DockerRuntime start(Command command, boolean readiness) throws IOException {
    String id =
        command
            .run(
                List.of(
                    "run",
                    "--detach",
                    "--platform",
                    "linux/amd64",
                    "--label",
                    "sdet-java-owned=true",
                    "--name",
                    "sdet-java-" + UUID.randomUUID(),
                    "--publish",
                    "127.0.0.1::3000",
                    IMAGE))
            .trim();
    if (!id.matches("[a-f0-9]{12,64}")) throw new IOException("Docker did not return container ID");
    DockerRuntime runtime = null;
    try {
      String port =
          command
              .run(
                  List.of(
                      "inspect",
                      "--format",
                      "{{(index (index .NetworkSettings.Ports \"3000/tcp\") 0).HostPort}}",
                      id))
              .trim();
      int number = Integer.parseInt(port);
      if (number <= 0 || number > 65535) throw new IOException("Invalid Docker port");
      String digest =
          command
              .run(List.of("image", "inspect", "--format", "{{index .RepoDigests 0}}", IMAGE))
              .trim();
      if (!digest.endsWith(IMAGE.substring(IMAGE.indexOf('@') + 1)))
        throw new IOException("Unexpected image digest");
      runtime = new DockerRuntime(command, id, URI.create("http://127.0.0.1:" + port), digest);
      if (readiness) waitReady(runtime.baseUrl.resolve("/dev/users"), Duration.ofSeconds(45));
      return runtime;
    } catch (IOException | RuntimeException error) {
      try {
        if (runtime != null) runtime.close();
        else command.run(List.of("rm", "--force", id));
      } catch (IOException cleanup) {
        cleanup.addSuppressed(error);
        throw cleanup;
      }
      throw error;
    }
  }

  public String id() {
    return id;
  }

  public URI baseUrl() {
    return baseUrl;
  }

  public String digest() {
    return digest;
  }

  public String logs() throws IOException {
    return command.run(List.of("logs", id));
  }

  @Override
  public synchronized void close() throws IOException {
    if (stopped) return;
    command.run(List.of("rm", "--force", id));
    stopped = true;
    if (Thread.currentThread() != shutdown)
      try {
        Runtime.getRuntime().removeShutdownHook(shutdown);
      } catch (IllegalStateException ignored) {
        /* shutdown already running */
      }
  }

  public static void waitReady(URI uri, Duration timeout) throws IOException {
    if (timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("Readiness timeout must be positive");
    long deadline = System.nanoTime() + timeout.toNanos();
    try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
      while (System.nanoTime() < deadline) {
        long remaining = deadline - System.nanoTime();
        try {
          var r =
              client.send(
                  HttpRequest.newBuilder(uri)
                      .timeout(Duration.ofNanos(Math.max(1, Math.min(2_000_000_000L, remaining))))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.discarding());
          if (r.statusCode() == 200) return;
        } catch (IOException ignored) {
          /* bounded retry */
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IOException("Readiness interrupted", ex);
        }
        try {
          Thread.sleep(Math.max(1, Math.min(250, (deadline - System.nanoTime()) / 1_000_000)));
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IOException("Readiness interrupted", ex);
        }
      }
    }
    throw new IOException("API readiness timed out");
  }

  public static String execute(List<String> args) throws IOException {
    var cmd = new ArrayList<String>();
    cmd.add("docker");
    cmd.addAll(args);
    var process = new ProcessBuilder(cmd).start();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var out = executor.submit(() -> capture(process.getInputStream()));
      var err = executor.submit(() -> capture(process.getErrorStream()));
      try {
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
          process.destroyForcibly();
          throw new IOException("Docker command timed out");
        }
        String stdout = out.get();
        String stderr = err.get();
        if (process.exitValue() != 0)
          throw new IOException(
              "Docker " + args.getFirst() + " failed (exit " + process.exitValue() + ")");
        return stdout + stderr;
      } catch (InterruptedException e) {
        process.destroyForcibly();
        Thread.currentThread().interrupt();
        throw new IOException("Docker command interrupted", e);
      } catch (ExecutionException e) {
        throw new IOException("Cannot capture Docker output", e);
      }
    }
  }

  private static String capture(InputStream stream) throws IOException {
    try (stream;
        var bytes = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = stream.read(buffer)) != -1) {
        if (bytes.size() + n > 16 * 1024 * 1024)
          throw new IOException("Docker output limit exceeded");
        bytes.write(buffer, 0, n);
      }
      return bytes.toString(StandardCharsets.UTF_8);
    }
  }
}
