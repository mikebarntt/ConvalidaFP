package es.convalidafp.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import es.convalidafp.catalog.DatasetPublicationService;
import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.DatasetVersion.Status;
import es.convalidafp.catalog.DatasetVersionRepository;
import es.convalidafp.testsupport.TestcontainersConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * La republicación es atómica: si publicar la nueva versión falla, el archivado de la anterior se
 * deshace.
 *
 * <p>Este test no es transaccional a propósito: necesita que la transacción del servicio sea la
 * única, para comprobar su rollback de verdad contra la base de datos. Por eso limpia al terminar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatasetPublicationRollbackTest {

  private static final String YEAR = "2031-32";

  @Autowired private DatasetPublicationService publication;
  @MockitoSpyBean private DatasetVersionRepository versions;

  @AfterEach
  void cleanUp() {
    versions.deleteAll(versions.findByAcademicYearOrderByIdAsc(YEAR));
  }

  @Test
  @DisplayName("Si la publicación falla, la versión anterior sigue publicada")
  void previousVersionStaysPublishedWhenPublicationFails() {
    DatasetVersion published = draft();
    published.publish(Instant.now());
    Long publishedId = versions.saveAndFlush(published).getId();
    Long draftId = versions.saveAndFlush(draft()).getId();

    // El archivado de la anterior se escribe bien; falla el guardado de la nueva.
    doThrow(new DataIntegrityViolationException("fallo simulado al publicar"))
        .when(versions)
        .saveAndFlush(argThat(v -> v != null && draftId.equals(v.getId())));

    assertThatThrownBy(() -> publication.publish(draftId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("fallo simulado");

    DatasetVersion stillPublished = versions.findByAcademicYearAndStatus(YEAR, Status.PUBLICADO).orElseThrow();
    assertThat(stillPublished.getId()).isEqualTo(publishedId);
    assertThat(stillPublished.getArchivedAt()).isNull();
    assertThat(versions.findById(draftId).orElseThrow().getStatus()).isEqualTo(Status.BORRADOR);
    assertThat(versions.findByAcademicYearAndStatus(YEAR, Status.ARCHIVADO)).isEmpty();
  }

  @Test
  @DisplayName("Sin fallo, la misma operación se confirma: nueva publicada y anterior archivada")
  void republishingCommitsWhenNothingFails() {
    DatasetVersion published = draft();
    published.publish(Instant.now());
    Long publishedId = versions.saveAndFlush(published).getId();
    Long draftId = versions.saveAndFlush(draft()).getId();

    publication.publish(draftId);

    assertThat(versions.findByAcademicYearAndStatus(YEAR, Status.PUBLICADO).orElseThrow().getId()).isEqualTo(draftId);
    assertThat(versions.findByAcademicYearAndStatus(YEAR, Status.ARCHIVADO).orElseThrow().getId())
        .isEqualTo(publishedId);
  }

  private static DatasetVersion draft() {
    return DatasetVersion.draft(YEAR, LocalDate.of(2026, 11, 14), "Revisor Ficticio", Instant.now());
  }
}
