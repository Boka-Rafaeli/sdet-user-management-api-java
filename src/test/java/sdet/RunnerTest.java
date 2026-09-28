package sdet;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class RunnerTest {
  public static class Probe {
    public static void main(String[] args) throws Exception {
      String mode = args[0];
      var settings =
          Settings.load(
              mode.equals("unaffected") ? Map.of("TEST_ENV", "prod") : Map.of(),
              mode.equals("strict") ? new String[0] : new String[] {"--baseline"});
      Baseline.Action body =
          () ->
              Baseline.check(
                  settings,
                  "BUG-001",
                  Set.of("dev"),
                  () -> {
                    if (!mode.equals("fixed")) fail("expected");
                  },
                  () -> {
                    if (mode.equals("changed")) fail("changed");
                  });
      if (mode.equals("network"))
        body =
            () -> {
              throw new IOException("transport");
            };
      if (mode.equals("setup"))
        body =
            () -> {
              throw new IOException("setup failed");
            };
      if (mode.equals("async"))
        body =
            () ->
                java.util.concurrent.CompletableFuture.failedFuture(
                        new IOException("async transport"))
                    .join();
      if (mode.equals("secret"))
        body =
            () -> {
              throw new IOException(
                  "credential " + System.getenv().getOrDefault("AUTH_TOKEN", "mysecrettoken"));
            };
      if (mode.equals("skip")) body = () -> Assumptions.abort("skip");
      var cases =
          List.of(
              new SuiteRunner.Case(
                  "case<&\"",
                  body,
                  () -> {
                    if (mode.equals("cleanup")) throw new IOException("cleanup");
                  }));
      boolean ok =
          SuiteRunner.run(
              mode.equals("empty")
                  ? List.of()
                  : mode.equals("duplicate") ? List.of(cases.getFirst(), cases.getFirst()) : cases,
              mode.equals("missing") ? List.of("case<&\"", "missing-case") : List.of("case<&\""),
              Path.of(args[1]),
              "probe",
              "test-digest");
      System.exit(ok ? 0 : 1);
    }
  }

  @Test
  void realJvmReportsAndExitCodes() throws Exception {
    for (String mode :
        List.of(
            "exact",
            "strict",
            "unaffected",
            "fixed",
            "changed",
            "network",
            "async",
            "setup",
            "cleanup",
            "secret",
            "skip",
            "empty",
            "duplicate",
            "missing")) {
      Path dir = Files.createTempDirectory("sdet-runner-");
      var process =
          JvmSupport.process(Probe.class, mode, dir.toString()).redirectErrorStream(true).start();
      String log =
          new String(
              process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      assertEquals(mode.equals("exact") ? 0 : 1, process.waitFor(), mode + log);
      var summary = Json.parse(Files.readString(dir.resolve("summary.json")));
      assertEquals(mode.equals("exact"), summary.get("complete").booleanValue());
      var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.newDocumentBuilder().parse(dir.resolve("junit.xml").toFile());
      assertTrue(Files.readString(dir.resolve("report.html")).contains("<!doctype html>"));
      if (mode.equals("secret"))
        for (String file : List.of("summary.json", "junit.xml", "report.html"))
          assertFalse(
              Files.readString(dir.resolve(file))
                  .contains(System.getenv().getOrDefault("AUTH_TOKEN", "mysecrettoken")));
      if (!mode.equals("exact"))
        assertTrue(Files.readString(dir.resolve("junit.xml")).contains("<failure"), mode);
      if (mode.equals("exact")) {
        assertTrue(Files.readString(dir.resolve("junit.xml")).contains("XFAIL"));
        assertTrue(Files.readString(dir.resolve("report.html")).contains("case&lt;&amp;&quot;"));
      }
    }
  }

  @Test
  void manifestCompleteness() {
    var pass = new Reports.Entry("a", "PASS", "", "");
    assertFalse(Reports.complete(List.of(), List.of()));
    assertFalse(Reports.complete(List.of("a", "b"), List.of(pass)));
    assertFalse(Reports.complete(List.of("a"), List.of(pass, pass)));
    assertFalse(Reports.complete(List.of("a", "a"), List.of(pass, pass)));
    assertTrue(Reports.complete(List.of("a"), List.of(pass)));
  }
}
