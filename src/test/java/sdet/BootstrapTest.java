package sdet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BootstrapTest {
  @Test
  void java25() {
    assertEquals(25, Runtime.version().feature());
  }
}
