package es.convalidafp.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.mariadb.MariaDBContainer;

/**
 * MariaDB real para los tests de integración: un único contenedor para toda la ejecución.
 *
 * <p>El contenedor se arranca una sola vez. Cada contexto de Spring (cada combinación distinta de
 * configuración de test) recibe su propia base de datos vacía dentro de ese contenedor, así que los
 * tests de un contexto no ven los datos de otro.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  private static final MariaDBContainer DATABASE_SERVER = new MariaDBContainer("mariadb:11.8");
  private static final AtomicInteger DATABASES = new AtomicInteger();

  static {
    DATABASE_SERVER.start();
  }

  @Bean
  DynamicPropertyRegistrar databaseProperties() {
    String database = "convalidafp_test_" + DATABASES.incrementAndGet();
    String serverUrl = DATABASE_SERVER.getJdbcUrl();
    String url = serverUrl.substring(0, serverUrl.lastIndexOf('/') + 1) + database;
    // En la imagen oficial, la contraseña de root es la misma que la del usuario de pruebas.
    String password = DATABASE_SERVER.getPassword();
    try (Connection connection = DriverManager.getConnection(serverUrl, "root", password);
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4");
    } catch (SQLException e) {
      throw new IllegalStateException("No se pudo crear la base de datos de test " + database, e);
    }
    return registry -> {
      registry.add("spring.datasource.url", () -> url);
      registry.add("spring.datasource.username", () -> "root");
      registry.add("spring.datasource.password", () -> password);
    };
  }

  /** Número de bases de datos creadas hasta ahora (una por contexto de Spring). */
  public static int databaseCount() {
    return DATABASES.get();
  }

  /** Identificador del único contenedor; no cambia durante toda la ejecución. */
  public static String containerId() {
    return DATABASE_SERVER.getContainerId();
  }
}
