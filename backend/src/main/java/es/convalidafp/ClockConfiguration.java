package es.convalidafp;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación. Inyectarlo, en lugar de pedir la hora al sistema, permite probarla.
 *
 * <p>Tiene precisión de microsegundos, la misma que guarda la base de datos: así una fecha es
 * idéntica recién creada y al volver a leerla.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

  @Bean
  Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
  }
}
