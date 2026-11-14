package es.convalidafp.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mariadb.MariaDBContainer;

/** MariaDB real en un contenedor, compartida por todos los tests de integración. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  MariaDBContainer mariaDbContainer() {
    return new MariaDBContainer("mariadb:11.8");
  }
}
