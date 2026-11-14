package es.convalidafp;

import es.convalidafp.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Arranca la aplicación entera: Flyway migra y Hibernate valida las entidades contra el esquema. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConvalidaFpApplicationTests {

  @Test
  void contextLoads() {}
}
