package es.convalidafp.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.convalidafp.admin.AuditEvent.Action;
import es.convalidafp.admin.AuditLog.Actor;
import es.convalidafp.catalog.DatasetVersion;
import es.convalidafp.catalog.DatasetVersionRepository;
import es.convalidafp.testsupport.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** La auditoría guarda quién, qué, sobre qué versión y cuándo, y solo admite altas. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AuditLogTest {

  @Autowired private AuditLog audit;
  @Autowired private AuditEventRepository events;
  @Autowired private DatasetVersionRepository versions;
  @Autowired private EntityManager em;

  @Test
  @DisplayName("Cada entrada guarda quién, qué acción, sobre qué versión, cuándo y desde qué IP")
  void eventsRecordWhoWhatWhichVersionAndWhen() {
    DatasetVersion version =
        versions.saveAndFlush(DatasetVersion.draft("2031-32", LocalDate.of(2026, 11, 14), "Revisor Ficticio", Instant.now()));
    Instant before = Instant.now().minusSeconds(1);

    AuditEvent published =
        audit.record(new Actor("admin-de-prueba", "10.0.0.7"), Action.PUBLICAR, version, 42L, "Publicada");
    AuditEvent failedLogin = audit.recordFailedLogin("intruso", "10.0.0.8", "Usuario o contraseña incorrectos");
    AuditEvent anonymous = audit.recordFailedLogin(" ", "10.0.0.9", null);

    assertThat(published.getId()).isNotNull();
    assertThat(published.getActor()).isEqualTo("admin-de-prueba");
    assertThat(published.getAction()).isEqualTo(Action.PUBLICAR);
    assertThat(published.getDatasetVersionId()).isEqualTo(version.getId());
    assertThat(published.getAcademicYear()).isEqualTo("2031-32");
    assertThat(published.getImportId()).isEqualTo(42L);
    assertThat(published.getIp()).isEqualTo("10.0.0.7");
    assertThat(published.getOccurredAt()).isAfter(before);

    assertThat(failedLogin.getAction()).isEqualTo(Action.LOGIN_FALLIDO);
    assertThat(failedLogin.getActor()).isEqualTo("intruso");
    assertThat(failedLogin.getDatasetVersionId()).isNull();
    assertThat(anonymous.getActor()).isEqualTo("(sin usuario)");

    // Las más recientes primero, y con límite.
    List<AuditEvent> latest = audit.latest(2);
    assertThat(latest).extracting(AuditEvent::getId).containsExactly(anonymous.getId(), failedLogin.getId());
    assertThat(audit.latest(0)).hasSize(1);
    assertThat(audit.latest(10_000).size()).isLessThanOrEqualTo(AuditLog.MAX_PAGE_SIZE);
  }

  @Test
  @DisplayName("Los textos demasiado largos se recortan en vez de fallar")
  void longValuesAreTruncated() {
    AuditEvent event = audit.recordFailedLogin("u".repeat(300), "10.0.0.1", "d".repeat(900));
    em.flush();
    assertThat(event.getActor()).hasSize(100);
    assertThat(event.getDetail()).hasSize(500);
  }

  @Test
  @DisplayName("La base de datos rechaza modificar una entrada de auditoría")
  void databaseRejectsUpdates() {
    AuditEvent event = audit.recordFailedLogin("intruso", "10.0.0.8", "Original");
    em.flush();

    assertThatThrownBy(
            () ->
                em.createNativeQuery("UPDATE audit_event SET actor = 'otro', detail = 'Cambiado' WHERE id = ?")
                    .setParameter(1, event.getId())
                    .executeUpdate())
        .isInstanceOf(PersistenceException.class)
        .hasStackTraceContaining("La auditoria no se puede modificar");
  }

  @Test
  @DisplayName("La base de datos rechaza borrar una entrada de auditoría")
  void databaseRejectsDeletes() {
    AuditEvent event = audit.recordFailedLogin("intruso", "10.0.0.8", "Original");
    em.flush();

    assertThatThrownBy(
            () ->
                em.createNativeQuery("DELETE FROM audit_event WHERE id = ?").setParameter(1, event.getId()).executeUpdate())
        .isInstanceOf(PersistenceException.class)
        .hasStackTraceContaining("La auditoria no se puede borrar");
  }

  @Test
  @DisplayName("El repositorio no ofrece ningún método para modificar ni borrar")
  void repositoryIsAppendOnly() {
    List<String> methods = Arrays.stream(AuditEventRepository.class.getMethods()).map(Method::getName).toList();
    assertThat(methods).contains("save").allMatch(name -> name.equals("save") || name.startsWith("find") || name.equals("count"));
    assertThat(methods).noneMatch(name -> name.startsWith("delete") || name.startsWith("update") || name.startsWith("remove"));
    // La entidad tampoco tiene ningún método para cambiar sus datos.
    assertThat(Arrays.stream(AuditEvent.class.getDeclaredMethods()).filter(m -> !m.isSynthetic()).map(Method::getName))
        .allMatch(name -> name.startsWith("get"));
  }
}
