package sdet;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ClientTest {
  @Test
  void realWireSemantics() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var seen = new LinkedBlockingQueue<List<String>>();
    server.createContext(
        "/",
        e -> {
          seen.add(
              List.of(
                  e.getRequestURI().getRawPath(),
                  Objects.toString(e.getRequestHeaders().getFirst("Authentication"), "MISSING"),
                  new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
          e.getResponseHeaders().set("Location", "/redirected");
          e.sendResponseHeaders(302, -1);
          e.close();
        });
    server.start();
    try (var client =
        new ApiClient(
            Settings.load(
                Map.of("BASE_URL", "http://127.0.0.1:" + server.getAddress().getPort())))) {
      assertEquals(302, client.delete("я+%/@example.com", "").status());
      assertEquals(List.of("/dev/users/%D1%8F%2B%25%2F%40example.com", "", ""), seen.take());
      client.create(Json.parse("null"));
      assertEquals("null", seen.take().get(2));
      client.delete("a@b", null);
      assertEquals("MISSING", seen.take().get(1));
      byte[] bytes = "héllo".getBytes(StandardCharsets.UTF_8);
      client.raw("POST", "/users", bytes, Map.of());
      assertEquals("héllo", seen.take().get(2));
      assertTrue(seen.isEmpty());
    } finally {
      server.stop(0);
    }
  }

  @SuppressWarnings("try")
  @Test
  void realTimeoutAndDisconnect() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/dev/users",
        e -> {
          try {
            Thread.sleep(300);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          e.close();
        });
    server.start();
    try (var client =
        new ApiClient(
            Settings.load(
                Map.of(
                    "BASE_URL",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "HTTP_TIMEOUT_SECONDS",
                    "0.05")))) {
      assertThrows(HttpTimeoutException.class, client::list);
    } finally {
      server.stop(0);
    }
    try (var socket = new java.net.ServerSocket(0);
        var client =
            new ApiClient(
                Settings.load(
                    Map.of(
                        "BASE_URL",
                        "http://127.0.0.1:" + socket.getLocalPort(),
                        "HTTP_TIMEOUT_SECONDS",
                        "0.1")))) {
      socket.close();
      assertThrows(java.io.IOException.class, client::list);
    }
  }

  @Test
  void exactEncoding() {
    assertEquals("a%2Bb%25%2F%40x", ApiClient.encode("a+b%/@x"));
  }
}
