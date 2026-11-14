package es.convalidafp.api;

import es.convalidafp.simulation.DegreeData;
import es.convalidafp.simulation.RuleData;
import es.convalidafp.simulation.SimulationEngine;
import es.convalidafp.simulation.SimulationInput;
import es.convalidafp.simulation.SimulationResult;
import es.convalidafp.simulation.TableData;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Ejecuta simulaciones sobre una versión publicada ya cargada en memoria. No toca la base de datos:
 * todo lo que necesita está en el {@link PublishedDataset} que recibe.
 */
@Service
public class SimulationService {

  /** Nombre de la métrica con el tiempo de simulación (Actuator / Micrometer). */
  public static final String TIMER_NAME = "convalidafp.simulation";

  private final SimulationEngine engine;
  private final Timer timer;

  SimulationService(SimulationEngine engine, MeterRegistry meters) {
    this.engine = engine;
    this.timer =
        Timer.builder(TIMER_NAME).description("Tiempo de cálculo de una simulación").register(meters);
  }

  /**
   * Simula el reconocimiento de un ciclo frente a un grado, asumiendo que el estudiante tiene el
   * título (todos los módulos superados).
   *
   * @throws ResourceNotFoundException si el ciclo o el grado no existen en esa versión
   */
  public SimulationResult simulate(PublishedDataset dataset, String cycleId, String degreeId) {
    dataset.requireCycle(cycleId);
    dataset.requireDegree(degreeId);
    DegreeData degree = dataset.degreeData().get(degreeId);
    TableData table = dataset.table(cycleId, degreeId).orElse(null);
    List<RuleData> rules = table == null ? List.of() : dataset.rules(table.id());
    SimulationInput input =
        SimulationInput.withFullTitle(
            cycleId, degreeId, dataset.info().academicYear(), dataset.info().versionId());
    return timer.record(() -> engine.simulate(input, table, rules, degree));
  }
}
