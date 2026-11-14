package es.convalidafp.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Decisión cerrada: el motor es dominio puro. Este test falla si alguna clase del paquete {@code
 * simulation} importa algo que no sea el propio paquete o la biblioteca estándar de Java (nada de
 * Spring, JPA, Hibernate ni otros paquetes de la aplicación).
 */
class SimulationPurityTest {

  private static final Path SOURCES = Path.of("src/main/java/es/convalidafp/simulation");

  @Test
  @DisplayName("El paquete simulation solo depende de java.* y de sí mismo")
  void simulationPackageHasNoFrameworkDependencies() throws IOException {
    List<String> forbidden = new ArrayList<>();
    int files = 0;
    try (Stream<Path> paths = Files.walk(SOURCES)) {
      for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        files++;
        for (String line : Files.readAllLines(file)) {
          String trimmed = line.trim();
          if (trimmed.startsWith("import ")
              && !trimmed.startsWith("import java.")
              && !trimmed.startsWith("import static java.")
              && !trimmed.startsWith("import es.convalidafp.simulation.")) {
            forbidden.add(file.getFileName() + ": " + trimmed);
          }
        }
      }
    }
    assertThat(files).as("ficheros del motor encontrados").isGreaterThan(5);
    assertThat(forbidden).as("imports no permitidos en el motor").isEmpty();
  }
}
