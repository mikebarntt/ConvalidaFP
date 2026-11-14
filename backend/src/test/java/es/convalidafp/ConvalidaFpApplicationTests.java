package es.convalidafp;

import static org.assertj.core.api.Assertions.assertThat;

import es.convalidafp.simulation.DegreeData;
import es.convalidafp.simulation.DegreeData.SubjectData;
import es.convalidafp.simulation.RuleData;
import es.convalidafp.simulation.SimulationEngine;
import es.convalidafp.simulation.SimulationInput;
import es.convalidafp.simulation.SimulationResult.Recognized;
import es.convalidafp.simulation.TableData;
import es.convalidafp.testsupport.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Arranca la aplicación entera: Flyway migra y Hibernate valida las entidades contra el esquema. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConvalidaFpApplicationTests {

  @Autowired private SimulationEngine engine;

  @Test
  void contextLoads() {}

  @Test
  @DisplayName("El motor recibe el tope del 25 % desde la configuración")
  void engineUsesTheConfiguredLegalCap() {
    DegreeData degree =
        new DegreeData(
            "UEJ-GP",
            "Grado de Prueba",
            new BigDecimal("240"),
            List.of(new SubjectData("A", "Asignatura de Prueba", new BigDecimal("6"), "OBLIGATORIA", 1)));
    TableData table = new TableData("T-EJ-01", TableData.Status.VIGENTE, null, null, null);
    RuleData rule = new RuleData("R-001", RuleData.TargetType.SUBJECT, "A", null, false, null, null, 1, null, Set.of());

    Recognized result =
        (Recognized)
            engine.simulate(SimulationInput.withFullTitle("CF-EJ", "UEJ-GP", "2026-27", 1L), table, List.of(rule), degree);

    assertThat(result.legalCapEcts()).isEqualByComparingTo("60");
  }
}
