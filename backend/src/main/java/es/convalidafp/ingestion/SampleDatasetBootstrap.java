package es.convalidafp.ingestion;

import es.convalidafp.catalog.DatasetPublicationService;
import es.convalidafp.catalog.DatasetVersionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Arranque local: si la base de datos está vacía, importa y publica el dataset de ejemplo.
 *
 * <p>Está desactivado por defecto ({@code convalidafp.bootstrap.sample-dataset.enabled=false} en
 * application.yml). Solo Docker Compose lo activa, con una variable de entorno, para el arranque
 * local. Cada subcarpeta de {@code convalidafp.bootstrap.sample-dataset.dir} es un curso. Si ya
 * existe alguna versión, no hace nada: nunca toca datos cargados por el panel de administración.
 */
@Component
@ConditionalOnProperty(name = "convalidafp.bootstrap.sample-dataset.enabled", havingValue = "true")
class SampleDatasetBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SampleDatasetBootstrap.class);

  private final Path directory;
  private final DatasetVersionRepository versions;
  private final DatasetImportService importer;
  private final DatasetPublicationService publication;

  SampleDatasetBootstrap(
      @Value("${convalidafp.bootstrap.sample-dataset.dir}") Path directory,
      DatasetVersionRepository versions,
      DatasetImportService importer,
      DatasetPublicationService publication) {
    this.directory = directory;
    this.versions = versions;
    this.importer = importer;
    this.publication = publication;
  }

  @Override
  public void run(ApplicationArguments args) throws IOException {
    if (versions.count() > 0) {
      log.info("La base de datos ya tiene datasets: no se carga el dataset de ejemplo");
      return;
    }
    if (!Files.isDirectory(directory)) {
      log.warn("No existe la carpeta del dataset de ejemplo: {}", directory);
      return;
    }
    List<Path> years;
    try (Stream<Path> paths = Files.list(directory)) {
      years = paths.filter(Files::isDirectory).sorted().toList();
    }
    for (Path year : years) {
      ImportResult result = importer.importDataset(DatasetFiles.fromDirectory(year));
      if (!result.accepted()) {
        log.error("El dataset de ejemplo {} no cumple el contrato: {}", year.getFileName(), result.report().errors());
        continue;
      }
      publication.publish(result.datasetVersionId());
      log.info(
          "Dataset de ejemplo {} importado y publicado como versión {} ({} avisos)",
          result.academicYear(),
          result.datasetVersionId(),
          result.report().warnings().size());
    }
  }
}
