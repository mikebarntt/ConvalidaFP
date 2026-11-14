package es.convalidafp.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Configuración web de la API: CORS y documentación OpenAPI. */
@Configuration(proxyBeanMethods = false)
class ApiConfiguration implements WebMvcConfigurer {

  private final String[] allowedOrigins;

  ApiConfiguration(@Value("${convalidafp.api.allowed-origins:}") String[] allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  /**
   * CORS restringido a los orígenes configurados. Sin ninguno configurado, la API solo se puede
   * llamar desde páginas servidas por su mismo dominio (el caso normal: Nginx sirve la SPA y la API).
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    if (allowedOrigins.length > 0) {
      registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("GET", "HEAD", "OPTIONS");
    }
  }

  @Bean
  OpenAPI convalidaFpOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ConvalidaFP API")
                .version("v1")
                .description(
                    "API pública de solo lectura del simulador de convalidaciones FP → Grado. Dado un"
                        + " ciclo de FP de grado superior y un grado universitario, indica qué asignaturas"
                        + " se reconocen, cuántos ECTS suman y de qué documento oficial sale cada"
                        + " reconocimiento.\n\n"
                        + "Los resultados son orientativos, no vinculantes: la resolución final"
                        + " corresponde a la universidad y el reconocimiento de créditos conlleva tasas.\n\n"
                        + "Límite: 60 peticiones por minuto por IP. Los errores usan el formato Problem"
                        + " Details (RFC 9457). Las respuestas llevan `ETag` ligado a la versión publicada"
                        + " del dataset."));
  }
}
