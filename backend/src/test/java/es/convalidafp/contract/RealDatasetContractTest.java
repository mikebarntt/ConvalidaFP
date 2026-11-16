package es.convalidafp.contract;

import static org.assertj.core.api.Assertions.assertThat;

import es.convalidafp.ingestion.DatasetFiles;
import es.convalidafp.ingestion.DatasetValidator;
import es.convalidafp.ingestion.ValidationReport;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprueba que el dataset real ({@code data/real/2026-27/}) cumple el contrato sin errores de
 * validación.
 */
class RealDatasetContractTest {

  private static final Path REAL_2026_27 =
      Path.of(System.getProperty("convalidafp.dataDir", "../data"))
          .resolve("real")
          .resolve("2026-27");

  @Test
  @DisplayName("El dataset real 2026-27 cumple el contrato sin errores")
  void realDatasetCompliesWithContract() throws IOException {
    ValidationReport report =
        new DatasetValidator().validate(DatasetFiles.fromDirectory(REAL_2026_27)).report();
    assertThat(report.errors()).as("errores: " + report.errors()).isEmpty();
  }
}
