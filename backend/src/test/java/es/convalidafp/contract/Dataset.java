package es.convalidafp.contract;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Lectura en bruto de un dataset (una carpeta de curso) para los tests del contrato.
 *
 * <p>Es código de test de la Fase 0: comprueba que el dataset de ejemplo cumple CONTRACT.md. El
 * importador real (paquete {@code ingestion}) se construye en la Fase 3.
 */
final class Dataset {

  /** Ficheros del contrato y su cabecera exacta, en orden. */
  static final Map<String, List<String>> HEADERS = new LinkedHashMap<>();

  static {
    HEADERS.put(
        "universities.csv",
        List.of("university_id", "name", "short_name", "ownership", "region", "website_url"));
    HEADERS.put(
        "fp_cycles.csv",
        List.of(
            "cycle_id", "name", "acronym", "professional_family", "regulation_ref", "total_ects"));
    HEADERS.put(
        "fp_modules.csv",
        List.of("module_id", "cycle_id", "official_code", "name", "ects", "course_year"));
    HEADERS.put(
        "degrees.csv",
        List.of(
            "degree_id",
            "university_id",
            "name",
            "degree_type",
            "center",
            "total_ects",
            "plan_code",
            "url"));
    HEADERS.put(
        "degree_subjects.csv",
        List.of(
            "subject_id",
            "degree_id",
            "name",
            "ects",
            "subject_type",
            "course_year",
            "official_code"));
    HEADERS.put(
        "source_documents.csv",
        List.of(
            "source_id",
            "document_type",
            "title",
            "issuer",
            "publication_date",
            "url",
            "file_sha256",
            "retrieved_at"));
    HEADERS.put(
        "recognition_tables.csv",
        List.of(
            "table_id",
            "cycle_id",
            "degree_id",
            "academic_year",
            "status",
            "source_id",
            "table_max_ects",
            "notes"));
    HEADERS.put(
        "recognition_rules.csv",
        List.of(
            "rule_id",
            "table_id",
            "target_type",
            "target_subject_id",
            "recognized_ects",
            "is_conditional",
            "condition_text",
            "source_id",
            "source_page",
            "source_excerpt"));
    HEADERS.put("rule_requirements.csv", List.of("rule_id", "module_id"));
  }

  /** Una fila de datos. {@code line} es el número de fila en el fichero (la cabecera es la 1). */
  record Row(String file, long line, Map<String, String> values) {
    String get(String column) {
      String value = values.get(column);
      return value == null ? "" : value;
    }

    boolean blank(String column) {
      return get(column).isEmpty();
    }
  }

  final Path dir;
  final List<String> errors = new ArrayList<>();
  private final Map<String, List<Row>> files = new LinkedHashMap<>();

  private Dataset(Path dir) {
    this.dir = dir;
  }

  static Dataset load(Path dir) {
    Dataset dataset = new Dataset(dir);
    HEADERS.forEach(dataset::loadFile);
    return dataset;
  }

  List<Row> rows(String file) {
    return files.getOrDefault(file, List.of());
  }

  private void loadFile(String name, List<String> expectedHeader) {
    Path path = dir.resolve(name);
    List<Row> rows = new ArrayList<>();
    files.put(name, rows);
    if (!Files.isRegularFile(path)) {
      errors.add(name + ": falta el fichero");
      return;
    }
    try {
      byte[] bytes = Files.readAllBytes(path);
      if (bytes.length >= 3
          && (bytes[0] & 0xFF) == 0xEF
          && (bytes[1] & 0xFF) == 0xBB
          && (bytes[2] & 0xFF) == 0xBF) {
        errors.add(name + ": el fichero tiene BOM; debe ser UTF-8 sin BOM");
        return;
      }
      String text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
      CSVFormat format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get();
      try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
        if (!parser.getHeaderNames().equals(expectedHeader)) {
          errors.add(
              name
                  + ": fila 1: cabecera incorrecta, se esperaba "
                  + String.join(",", expectedHeader));
          return;
        }
        for (CSVRecord record : parser) {
          long line = record.getRecordNumber() + 1;
          if (record.size() != expectedHeader.size()) {
            errors.add(
                name
                    + ": fila "
                    + line
                    + ": tiene "
                    + record.size()
                    + " columnas y se esperaban "
                    + expectedHeader.size());
            continue;
          }
          rows.add(new Row(name, line, record.toMap()));
        }
      }
    } catch (CharacterCodingException e) {
      errors.add(name + ": no es UTF-8 válido");
    } catch (IOException | RuntimeException e) {
      errors.add(name + ": no se puede leer como CSV (RFC 4180): " + e.getMessage());
    }
  }
}
