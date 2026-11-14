package es.convalidafp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import es.convalidafp.catalog.DatasetVersionRepository;
import es.convalidafp.testsupport.SampleFiles;
import es.convalidafp.testsupport.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * La carga del dataset de ejemplo (ficticio) está desactivada por defecto: con la configuración de
 * application.yml, sin más, la aplicación arranca con la base de datos vacía. Solo
 * docker-compose.yml la activa, con una variable de entorno, para el arranque local.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SampleDatasetBootstrapDisabledTest {

  @Autowired private ApplicationContext context;
  @Autowired private Environment environment;
  @Autowired private DatasetVersionRepository versions;

  @Test
  @DisplayName("Por defecto no existe el cargador y no se ha cargado ningún dataset")
  void bootstrapIsDisabledByDefault() {
    assertThat(environment.getProperty("convalidafp.bootstrap.sample-dataset.enabled")).isEqualTo("false");
    assertThat(context.getBeansOfType(SampleDatasetBootstrap.class)).isEmpty();
    assertThat(versions.count()).isZero();
  }

  @Test
  @DisplayName("application.yml la deja desactivada y solo docker-compose.yml la activa")
  void onlyDockerComposeEnablesIt() throws IOException {
    Path repository = SampleFiles.SAMPLE.resolve("../..").normalize();
    String applicationYml =
        Files.readString(repository.resolve("backend/src/main/resources/application.yml"), StandardCharsets.UTF_8);
    assertThat(applicationYml).containsPattern("sample-dataset:\\R(\\s*#.*\\R)*\\s*enabled: false");

    String compose = Files.readString(repository.resolve("docker-compose.yml"), StandardCharsets.UTF_8);
    assertThat(compose).contains("CONVALIDAFP_BOOTSTRAP_SAMPLE_DATASET_ENABLED: \"true\"");
    assertThat(compose).contains("CONVALIDAFP_BOOTSTRAP_SAMPLE_DATASET_DIR: /data/sample");
  }
}
