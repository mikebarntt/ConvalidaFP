package es.convalidafp.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.DatasetVersion.Status;
import es.convalidafp.catalog.DatasetVersionRepository;
import es.convalidafp.rules.RecognitionRuleRepository;
import es.convalidafp.testsupport.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Arranque local: con la propiedad definida y la base de datos vacía, la aplicación arranca con el
 * dataset de ejemplo ya cargado y publicado.
 */
@SpringBootTest(properties = "convalidafp.bootstrap.sample-dataset-dir=${convalidafp.dataDir:../data}/sample")
@Import(TestcontainersConfiguration.class)
class SampleDatasetBootstrapTest {

  @Autowired private DatasetVersionRepository versions;
  @Autowired private RecognitionRuleRepository rules;
  @Autowired private SampleDatasetBootstrap bootstrap;

  @Test
  @DisplayName("Al arrancar se importan y publican los dos cursos del dataset de ejemplo")
  void sampleDatasetIsLoadedAndPublishedOnStartup() {
    List<DatasetVersion> all = versions.findAll();
    assertThat(all).extracting(DatasetVersion::getAcademicYear).containsExactlyInAnyOrder("2025-26", "2026-27");
    assertThat(all).allMatch(DatasetVersion::isPublished);

    DatasetVersion current = versions.findFirstByStatusOrderByAcademicYearDesc(Status.PUBLICADO).orElseThrow();
    assertThat(current.getAcademicYear()).isEqualTo("2026-27");
    assertThat(rules.countByDatasetVersionId(current.getId())).isEqualTo(54);
  }

  @Test
  @DisplayName("Si ya hay datasets, un segundo arranque no carga nada")
  void secondStartupLoadsNothing() throws Exception {
    long before = versions.count();
    bootstrap.run(new DefaultApplicationArguments());
    assertThat(versions.count()).isEqualTo(before);
  }
}
