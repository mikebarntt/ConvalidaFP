package es.convalidafp;

import es.convalidafp.simulation.SimulationEngine;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conecta el motor de simulación con la configuración. Vive fuera del paquete {@code simulation}
 * para que el motor siga sin depender de Spring.
 */
@Configuration(proxyBeanMethods = false)
public class SimulationEngineConfiguration {

  /** El tope legal (0.25 en la v1) se lee de la configuración, no de un número fijo en el código. */
  @Bean
  SimulationEngine simulationEngine(
      @Value("${convalidafp.simulation.max-recognition-ratio}") BigDecimal maxRecognitionRatio) {
    return new SimulationEngine(maxRecognitionRatio);
  }
}
