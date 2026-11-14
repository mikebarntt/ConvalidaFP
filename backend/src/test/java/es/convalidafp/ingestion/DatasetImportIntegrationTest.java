package es.convalidafp.ingestion;

import static es.convalidafp.ingestion.DatasetContract.DEGREES;
import static es.convalidafp.ingestion.DatasetContract.MODULES;
import static es.convalidafp.ingestion.DatasetContract.REQUIREMENTS;
import static es.convalidafp.ingestion.DatasetContract.RULES;
import static es.convalidafp.ingestion.DatasetContract.SUBJECTS;
import static es.convalidafp.ingestion.DatasetContract.TABLES;
import static es.convalidafp.ingestion.DatasetContract.UNIVERSITIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.convalidafp.catalog.DatasetPublicationService;
import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.DatasetVersion.Status;
import es.convalidafp.catalog.DatasetVersionRepository;
import es.convalidafp.catalog.DegreeSubjectRepository;
import es.convalidafp.catalog.UniversityRepository;
import es.convalidafp.ingestion.DatasetDiff.FieldChange;
import es.convalidafp.ingestion.DatasetDiff.FileDiff;
import es.convalidafp.ingestion.DatasetDiff.RowChange;
import es.convalidafp.ingestion.ValidationReport.Issue;
import es.convalidafp.rules.RecognitionRule;
import es.convalidafp.rules.RecognitionRuleRepository;
import es.convalidafp.rules.RecognitionTableRepository;
import es.convalidafp.testsupport.SampleFiles;
import es.convalidafp.testsupport.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fase 3 de punta a punta contra MariaDB: validar, guardar como borrador, comparar con lo publicado,
 * publicar y descartar. Cada test corre en una transacción que se deshace al terminar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DatasetImportIntegrationTest {

  @Autowired private DatasetImportService importer;
  @Autowired private DatasetPublicationService publication;
  @Autowired private DatasetVersionRepository versions;
  @Autowired private UniversityRepository universities;
  @Autowired private DegreeSubjectRepository subjects;
  @Autowired private RecognitionTableRepository tables;
  @Autowired private RecognitionRuleRepository rules;
  @Autowired private EntityManager em;

  // ---------- importar ----------

  @Test
  @DisplayName("Un dataset válido queda guardado como borrador, con todas sus filas")
  void validDatasetIsStoredAsDraft() {
    ImportResult result = importer.importDataset(SampleFiles.of("2026-27").build());

    assertThat(result.accepted()).isTrue();
    assertThat(result.academicYear()).isEqualTo("2026-27");
    assertThat(result.report().errors()).isEmpty();
    DatasetVersion version = versions.findById(result.datasetVersionId()).orElseThrow();
    assertThat(version.getStatus()).isEqualTo(Status.BORRADOR);
    assertThat(version.getAcademicYear()).isEqualTo("2026-27");
    assertThat(version.getReviewer()).isEqualTo("Revisor Ficticio");
    assertThat(version.getManifestDate()).hasToString("2026-11-13");

    Long id = version.getId();
    assertThat(universities.countByDatasetVersionId(id)).isEqualTo(3);
    assertThat(subjects.countByDatasetVersionId(id)).isEqualTo(183);
    assertThat(tables.countByDatasetVersionId(id)).isEqualTo(8);
    assertThat(rules.countByDatasetVersionId(id)).isEqualTo(54);
    RecognitionRule andRule = rules.findByDatasetVersionIdAndCode(id, "R-002").orElseThrow();
    assertThat(andRule.getRequirements()).extracting(r -> r.getModule().getCode()).containsExactlyInAnyOrder("CF-EJ-M02", "CF-EJ-M03");
    assertThat(rules.findByDatasetVersionIdAndCode(id, "R-053").orElseThrow().getEffectiveSource().getCode())
        .isEqualTo("S-EJ-04");
    // Importar no publica: sigue sin haber curso vigente.
    assertThat(versions.findFirstByStatusOrderByAcademicYearDesc(Status.PUBLICADO)).isEmpty();
  }

  @Test
  @DisplayName("Los avisos no bloquean la carga y llegan en el informe")
  void warningsDoNotBlockTheImport() {
    ImportResult result = importer.importDataset(SampleFiles.of("2026-27").build());
    assertThat(result.accepted()).isTrue();
    assertThat(result.report().warnings()).extracting(Issue::code).containsExactlyInAnyOrder("A1", "A2");
  }

  @Test
  @DisplayName("Un solo error rechaza la carga entera: no se guarda nada")
  void oneErrorRejectsTheWholeImport() {
    long versionsBefore = versions.count();
    long universitiesBefore = universities.count();

    ImportResult result = importer.importDataset(SampleFiles.of("2026-27").replace(MODULES, ",14,1", ",0,1").build());

    assertThat(result.accepted()).isFalse();
    assertThat(result.datasetVersionId()).isNull();
    assertThat(result.diff()).isNull();
    assertThat(result.academicYear()).isEqualTo("2026-27");
    assertThat(result.report().errors()).hasSize(1);
    Issue error = result.report().errors().get(0);
    assertThat(error.code()).isEqualTo("V3");
    assertThat(error.file()).isEqualTo(MODULES);
    assertThat(error.row()).isEqualTo(2L);
    assertThat(error.column()).isEqualTo("ects");
    assertThat(versions.count()).isEqualTo(versionsBefore);
    assertThat(universities.count()).isEqualTo(universitiesBefore);
  }

  @Test
  @DisplayName("Se puede importar desde un ZIP")
  void importFromZip() throws IOException {
    byte[] zip = SampleFiles.of("2025-26").toZip("2025-26");
    ImportResult result = importer.importDataset(DatasetFiles.fromZip(new ByteArrayInputStream(zip)));
    assertThat(result.accepted()).isTrue();
    assertThat(rules.countByDatasetVersionId(result.datasetVersionId())).isEqualTo(6);
  }

  // ---------- diferencias ----------

  @Test
  @DisplayName("Sin nada publicado, todo son altas")
  void withoutBaselineEverythingIsAdded() {
    DatasetDiff diff = importer.importDataset(SampleFiles.of("2025-26").build()).diff();

    assertThat(diff.baselineVersionId()).isNull();
    assertThat(diff.baselineAcademicYear()).isNull();
    assertThat(diff.file(UNIVERSITIES).added()).containsExactly("UEJ");
    assertThat(diff.file(RULES).added()).containsExactly("R-001", "R-002", "R-003", "R-004", "R-005", "R-006");
    assertThat(diff.files()).allMatch(f -> f.removed().isEmpty() && f.changed().isEmpty());
    assertThat(diff.isEmpty()).isFalse();
  }

  @Test
  @DisplayName("Un curso nuevo se compara con el curso anterior publicado: altas y asignatura renombrada")
  void newYearIsComparedWithThePreviousPublishedYear() {
    Long previousId = importAndPublish(SampleFiles.of("2025-26"));

    DatasetDiff diff = importer.importDataset(SampleFiles.of("2026-27").build()).diff();

    assertThat(diff.baselineVersionId()).isEqualTo(previousId);
    assertThat(diff.baselineAcademicYear()).isEqualTo("2025-26");
    assertThat(diff.file(UNIVERSITIES).added()).containsExactly("UFI", "UPR");
    assertThat(diff.file(DEGREES).added()).containsExactly("UEJ-DG", "UEJ-GP-SUR", "UFI-GS", "UPR-GD");
    assertThat(diff.file(DEGREES).changed()).isEmpty();

    // Caso 11: la asignatura renombrada aparece como cambio, con el nombre de antes y el de ahora.
    FileDiff subjectDiff = diff.file(SUBJECTS);
    assertThat(subjectDiff.changed()).hasSize(1);
    RowChange renamed = subjectDiff.changed().get(0);
    assertThat(renamed.id()).isEqualTo("UEJ-GP-WEB");
    assertThat(renamed.fields())
        .containsExactly(new FieldChange("name", "Programación Web de Prueba", "Desarrollo Web de Prueba"));
    assertThat(subjectDiff.removed()).isEmpty();

    // La tabla común no cambia aunque cambie su curso; las reglas R-001 a R-006 tampoco.
    assertThat(diff.file(TABLES).changed()).isEmpty();
    assertThat(diff.file(TABLES).added()).hasSize(7);
    assertThat(diff.file(RULES).changed()).isEmpty();
    assertThat(diff.file(RULES).added()).hasSize(48).contains("R-007", "R-053").doesNotContain("R-001");
  }

  @Test
  @DisplayName("Volver a importar lo ya publicado no produce ninguna diferencia")
  void reimportingThePublishedDatasetGivesNoDifferences() {
    Long publishedId = importAndPublish(SampleFiles.of("2026-27"));

    ImportResult result = importer.importDataset(SampleFiles.of("2026-27").build());

    assertThat(result.diff().baselineVersionId()).isEqualTo(publishedId);
    assertThat(result.diff().files()).as("diferencias: " + result.diff()).allMatch(FileDiff::isEmpty);
    assertThat(result.diff().isEmpty()).isTrue();
    assertThat(result.datasetVersionId()).isNotEqualTo(publishedId);
  }

  @Test
  @DisplayName("Una corrección del mismo curso muestra exactamente lo que cambió: altas, cambios y bajas")
  void correctionShowsExactlyWhatChanged() {
    importAndPublish(SampleFiles.of("2025-26"));
    Long publishedId = importAndPublish(SampleFiles.of("2026-27"));

    SampleFiles corrected =
        SampleFiles.of("2026-27")
            .replace(TABLES, "S-PR-01,30,", "S-PR-01,24.50,")
            .replace(RULES, "R-051,T-PR-02,SUBJECT,UPR-GD-CLOUD,,false,,,17,Módulos de Prueba 04 y 07 - Nube de Demostración\n", "")
            .replace(REQUIREMENTS, "R-051,CF-PR-M04\n", "")
            .replace(REQUIREMENTS, "R-051,CF-PR-M07\n", "")
            .replace(REQUIREMENTS, "R-046,CF-PR-M01", "R-046,CF-PR-M02")
            .append(RULES, "R-055,T-PR-02,SUBJECT,UPR-GD-WEB,,false,,,17,Módulo de Prueba 04 - Tecnologías Web")
            .append(REQUIREMENTS, "R-055,CF-PR-M04")
            .append(REQUIREMENTS, "R-055,CF-PR-M07");
    ImportResult result = importer.importDataset(corrected.build());

    assertThat(result.accepted()).as("errores: " + result.report().errors()).isTrue();
    DatasetDiff diff = result.diff();
    // Se compara con la versión publicada del mismo curso, no con el curso anterior.
    assertThat(diff.baselineVersionId()).isEqualTo(publishedId);
    assertThat(diff.baselineAcademicYear()).isEqualTo("2026-27");

    assertThat(diff.file(TABLES).changed())
        .containsExactly(new RowChange("T-PR-02", List.of(new FieldChange("table_max_ects", "30", "24.5"))));
    assertThat(diff.file(RULES).added()).containsExactly("R-055");
    assertThat(diff.file(RULES).removed()).containsExactly("R-051");
    assertThat(diff.file(RULES).changed())
        .containsExactly(
            new RowChange("R-046", List.of(new FieldChange(DatasetDiff.REQUIRED_MODULES, "CF-PR-M01", "CF-PR-M02"))));
    for (String untouched : List.of(UNIVERSITIES, DEGREES, SUBJECTS, MODULES)) {
      assertThat(diff.file(untouched).isEmpty()).as(untouched).isTrue();
    }
  }

  // ---------- publicar y descartar ----------

  @Test
  @DisplayName("Importar y publicar una corrección archiva la versión anterior del curso")
  void publishingACorrectionArchivesThePreviousVersion() {
    Long firstId = importAndPublish(SampleFiles.of("2026-27"));
    Long secondId = importAndPublish(SampleFiles.of("2026-27").replace(TABLES, "S-PR-01,30,", "S-PR-01,24,"));
    em.clear();

    assertThat(versions.findByAcademicYearAndStatus("2026-27", Status.PUBLICADO).orElseThrow().getId()).isEqualTo(secondId);
    assertThat(versions.findById(firstId).orElseThrow().getStatus()).isEqualTo(Status.ARCHIVADO);
    assertThat(tables.findByDatasetVersionIdAndCode(secondId, "T-PR-02").orElseThrow().getTableMaxEcts())
        .isEqualByComparingTo("24");
    assertThat(tables.findByDatasetVersionIdAndCode(firstId, "T-PR-02").orElseThrow().getTableMaxEcts())
        .isEqualByComparingTo("30");
  }

  @Test
  @DisplayName("Descartar un borrador borra la versión y todos sus datos, y nada más")
  void discardingADraftDeletesItsData() {
    Long publishedId = importAndPublish(SampleFiles.of("2025-26"));
    Long draftId = importer.importDataset(SampleFiles.of("2026-27").build()).datasetVersionId();
    assertThat(rules.countByDatasetVersionId(draftId)).isEqualTo(54);

    importer.discardDraft(draftId);

    assertThat(versions.findById(draftId)).isEmpty();
    assertThat(rules.countByDatasetVersionId(draftId)).isZero();
    assertThat(subjects.countByDatasetVersionId(draftId)).isZero();
    assertThat(universities.countByDatasetVersionId(draftId)).isZero();
    assertThat(requirementCount(draftId)).isZero();
    // La versión publicada sigue intacta.
    assertThat(rules.countByDatasetVersionId(publishedId)).isEqualTo(6);
    assertThat(requirementCount(publishedId)).isEqualTo(7);
  }

  @Test
  @DisplayName("Solo se descartan borradores: ni versiones publicadas, ni archivadas, ni inexistentes")
  void onlyDraftsCanBeDiscarded() {
    Long firstId = importAndPublish(SampleFiles.of("2025-26"));
    Long secondId = importAndPublish(SampleFiles.of("2025-26"));

    assertThatThrownBy(() -> importer.discardDraft(secondId)).isInstanceOf(IllegalStateException.class).hasMessageContaining("PUBLICADO");
    assertThatThrownBy(() -> importer.discardDraft(firstId)).isInstanceOf(IllegalStateException.class).hasMessageContaining("ARCHIVADO");
    assertThatThrownBy(() -> importer.discardDraft(-1L)).isInstanceOf(IllegalArgumentException.class);
    assertThat(rules.countByDatasetVersionId(firstId)).isEqualTo(6);
  }

  // ---------- utilidades ----------

  private Long importAndPublish(SampleFiles files) {
    ImportResult result = importer.importDataset(files.build());
    assertThat(result.accepted()).as("errores: " + result.report().errors()).isTrue();
    publication.publish(result.datasetVersionId());
    em.flush();
    em.clear();
    return result.datasetVersionId();
  }

  private long requirementCount(Long versionId) {
    return em.createQuery("select count(r) from RuleRequirement r where r.datasetVersion.id = :id", Long.class)
        .setParameter("id", versionId)
        .getSingleResult();
  }
}
