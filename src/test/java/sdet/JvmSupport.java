package sdet;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.*;

/** Include the active JaCoCo agent so child JVM evidence contributes to branch review. */
final class JvmSupport {
  private JvmSupport() {}

  static ProcessBuilder process(Class<?> main, String... args) {
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments())
      if (argument.startsWith("-javaagent:") && argument.contains("jacoco")) command.add(argument);
    command.add("-Djqwik.database=target/probe-database");
    command.add("-cp");
    command.add(
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
    command.add(main.getName());
    command.addAll(Arrays.asList(args));
    return new ProcessBuilder(command);
  }
}
