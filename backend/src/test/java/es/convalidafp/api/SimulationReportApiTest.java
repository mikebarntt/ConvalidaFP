package es.convalidafp.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfArray;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

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
  @DisplayName("subjects_complete = false: el PDF da los ECTS pendientes y la web del grado en vez de las restantes")
  void reportWithIncompleteSubjectList() throws Exception {
    byte[] pdf = ok(URL + "?cycle=CF-EJ&degree=UEJ-GP-SUR");
    assertThat(text(pdf))
        .contains("Te quedan 217,5 ECTS por cursar.")
        .contains("Consulta el plan de estudios completo en la web de la universidad")
        .contains("https://uej.example/sur/grados/prueba")
        .doesNotContain("Asignaturas restantes");
    // El enlace es pulsable: anotación de enlace con la URL del grado.
    assertThat(links(pdf)).containsExactly("https://uej.example/sur/grados/prueba");

    // Con subjects_complete = true se mantiene la lista.
    assertThat(text(ok(URL + "?cycle=CF-EJ&degree=UEJ-GP"))).contains("Asignaturas restantes").doesNotContain("Te quedan");
  }

  @Test
  @DisplayName("subjects_complete = false y grado sin url: el PDF da solo el texto, sin enlace")
  void reportWithIncompleteSubjectListAndNoUrl() throws Exception {
    JsonNode response = getJson("/api/v1/simulations?cycle=CF-EJ&degree=UEJ-GP-SUR");
    ((ObjectNode) response.get("degree")).putNull("url");
    byte[] pdf = SimulationReportPdf.render(json.treeToValue(response, SimulationDtos.SimulationResponse.class));

    assertThat(text(pdf))
        .contains("Te quedan 217,5 ECTS por cursar. Consulta el plan de estudios completo en la web de la universidad.")
        .doesNotContain("https://uej.example/sur/grados/prueba");
    assertThat(links(pdf)).isEmpty();
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

  /** URL de los enlaces (anotaciones /URI) de todas las páginas. */
  private static List<String> links(byte[] pdf) throws Exception {
    List<String> urls = new ArrayList<>();
    try (PdfReader reader = new PdfReader(pdf)) {
      for (int page = 1; page <= reader.getNumberOfPages(); page++) {
        PdfArray annotations = reader.getPageN(page).getAsArray(PdfName.ANNOTS);
        for (int i = 0; annotations != null && i < annotations.size(); i++) {
          PdfDictionary annotation = (PdfDictionary) PdfReader.getPdfObject(annotations.getPdfObject(i));
          PdfDictionary action = annotation.getAsDict(PdfName.A);
          if (action != null && action.getAsString(PdfName.URI) != null) {
            urls.add(action.getAsString(PdfName.URI).toString());
          }
        }
      }
    }
    return urls;
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
