package es.convalidafp;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj de la aplicación. Inyectarlo, en lugar de pedir la hora al sistema, permite probarla. */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
