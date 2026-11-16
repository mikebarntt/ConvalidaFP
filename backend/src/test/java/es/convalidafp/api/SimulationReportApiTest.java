package es.convalidafp.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Informe PDF de una simulación: GET /api/v1/simulations/report.pdf. */
class SimulationReportApiTest extends ApiTestSupport {

  private static final String URL = "/api/v1/simulations/report.pdf";

  @Test
  @DisplayName("CF-EJ × UEJ-GP: el PDF contiene 51 ECTS, el aviso, las fuentes, los condicionados y las restantes")
  void reportOfRecognizedSimulation() throws Exception {
    MockHttpServletResponse response = perform(URL + "?cycle=CF-EJ&degree=UEJ-GP").getResponse();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).isEqualTo("application/pdf");
    assertThat(response.getHeader("Content-Disposition"))
        .isEqualTo("attachment; filename=\"convalidafp-CF-EJ-UEJ-GP.pdf\"");
    assertThat(response.getHeader("ETag")).isNotBlank();

    byte[] pdf = response.getContentAsByteArray();
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    String text = text(pdf);
    assertThat(text)
        .contains("51 ECTS")
        .contains("de 240 ECTS reconocidos")
        .contains(SimulationDtos.NOTICE)
        .contains("Asignaturas reconocidas")
        .contains("1.º curso")
        // Fuente de R-001: documento, página y fragmento literal.
        .contains("Convenio Ficticio de Reconocimiento entre la Comunidad de Ejemplo y la Universidad de Ejemplo")
        .contains("página 3")
        .contains("«Módulo Ficticio 01 - Programación I»")
        .contains("Créditos optativos")
        .contains("Reconocimientos condicionados")
        .contains("Solo si las prácticas son de naturaleza similar")
        .contains("Asignaturas restantes");

    // R-054: regla condicionada cuyo documento no explica la condición.
    assertThat(text(ok(URL + "?cycle=CF-EJ&degree=UEJ-GP-SUR"))).contains("condición no especificada en el documento");
  }

  @Test
  @DisplayName("Resultado limitado: el PDF lo dice y titula la lista con el tope")
  void reportOfCappedSimulation() throws Exception {
    String text = text(ok(URL + "?cycle=CF-EJ&degree=UPR-GD"));
    assertThat(text)
        .contains("60 ECTS de 240 ECTS reconocibles")
        .contains("El resultado está limitado por el tope")
        .contains("Las tablas suman 66 ECTS")
        .contains("Reconocibles hasta el tope de 60 ECTS");

    assertThat(text(ok(URL + "?cycle=CF-PR&degree=UPR-GD"))).contains("el tope propio de esta tabla de reconocimiento");
  }

  @Test
  @DisplayName("Sin convenio y sin datos también tienen informe, con el aviso")
  void reportOfNoAgreementAndNoData() throws Exception {
    assertThat(text(ok(URL + "?cycle=CF-EJ&degree=UFI-GS")))
        .contains("No existe tabla de reconocimiento firmada")
        .contains("no existe tabla firmada")
        .contains(SimulationDtos.NOTICE);
    assertThat(text(ok(URL + "?cycle=CF-PR&degree=UEJ-GP")))
        .contains("Todavía no tenemos datos de esta combinación")
        .contains(SimulationDtos.NOTICE);
  }

  @Test
  @DisplayName("Errores en Problem Details: ciclo inexistente (404) y parámetro que falta (400)")
  void reportErrors() throws Exception {
    MockHttpServletResponse notFound = perform(URL + "?cycle=CF-NO&degree=UEJ-GP").getResponse();
    assertThat(notFound.getStatus()).isEqualTo(404);
    assertThat(notFound.getContentType()).startsWith("application/problem+json");
    assertThat(notFound.getContentAsString(StandardCharsets.UTF_8)).contains("el ciclo «CF-NO»");

    assertThat(perform(URL + "?degree=UEJ-GP").getResponse().getStatus()).isEqualTo(400);
  }

  private byte[] ok(String url) throws Exception {
    MockHttpServletResponse response = perform(url).getResponse();
    assertThat(response.getStatus()).as(url).isEqualTo(200);
    return response.getContentAsByteArray();
  }

  /** Texto de todas las páginas, con los saltos de línea y espacios repetidos reducidos a uno. */
  private static String text(byte[] pdf) throws Exception {
    try (PdfReader reader = new PdfReader(pdf)) {
      PdfTextExtractor extractor = new PdfTextExtractor(reader);
      StringBuilder all = new StringBuilder();
      for (int page = 1; page <= reader.getNumberOfPages(); page++) {
        all.append(extractor.getTextFromPage(page)).append(' ');
      }
      return all.toString().replaceAll("\\s+", " ");
    }
  }
}
