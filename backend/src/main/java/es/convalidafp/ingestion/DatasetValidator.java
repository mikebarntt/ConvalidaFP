package es.convalidafp.ingestion;

import static es.convalidafp.ingestion.DatasetContract.CYCLES;
import static es.convalidafp.ingestion.DatasetContract.DEGREES;
import static es.convalidafp.ingestion.DatasetContract.MANIFEST;
import static es.convalidafp.ingestion.DatasetContract.MODULES;
import static es.convalidafp.ingestion.DatasetContract.REQUIREMENTS;
import static es.convalidafp.ingestion.DatasetContract.RULES;
import static es.convalidafp.ingestion.DatasetContract.SOURCES;
import static es.convalidafp.ingestion.DatasetContract.SUBJECTS;
import static es.convalidafp.ingestion.DatasetContract.TABLES;
import static es.convalidafp.ingestion.DatasetContract.UNIVERSITIES;

import es.convalidafp.ingestion.RawDataset.DataRow;
import es.convalidafp.ingestion.ValidationReport.Issue;
import es.convalidafp.ingestion.ValidationReport.Severity;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Lee un dataset y lo valida contra CONTRACT.md.
 *
 * <p>Cada incidencia lleva el código de su validación (V1–V12, F1–F11, A1–A3), el fichero, la fila,
 * la columna y el motivo. Los errores bloquean la carga; los avisos, no.
 */
@Component
public class DatasetValidator {

  /** El dataset leído y su informe. El dataset solo es fiable si el informe no tiene errores. */
  public record ValidatedDataset(RawDataset dataset, ValidationReport report) {}

  private static final Pattern ID = Pattern.compile("[A-Z0-9]+(-[A-Z0-9]+)*");
  private static final Pattern ACADEMIC_YEAR = Pattern.compile("\\d{4}-\\d{2}");
  private static final Pattern DECIMAL = Pattern.compile("\\d{1,4}(\\.\\d{1,2})?");
  private static final Pattern POSITIVE_INT = Pattern.compile("[1-9]\\d{0,8}");
  private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
  private static final int MAX_EXCERPT = 200;
  private static final Set<String> SHORT_COLUMNS = Set.of("official_code", "acronym", "short_name", "plan_code");

  private final ObjectMapper json = new ObjectMapper();

  public ValidatedDataset validate(DatasetFiles files) {
    return new Run(files).run();
  }

  /** Una validación en curso: acumula las incidencias de un dataset. */
  private final class Run {

    private final DatasetFiles files;
    private final List<Issue> errors = new ArrayList<>();
    private final List<Issue> warnings = new ArrayList<>();
    private final Map<String, List<DataRow>> rowsByFile = new LinkedHashMap<>();
    private String academicYear;
    private LocalDate manifestDate;
    private String reviewer;

    private Run(DatasetFiles files) {
      this.files = files;
    }

    private ValidatedDataset run() {
      DatasetContract.HEADERS.forEach(this::readFile);
      checkLengths();
      checkManifest();
      checkContent();
      RawDataset dataset = new RawDataset(academicYear, manifestDate, reviewer, rowsByFile);
      return new ValidatedDataset(dataset, new ValidationReport(errors, warnings));
    }

    // ---------- lectura (F1, F2, F3) ----------

    private void readFile(String name, List<String> expectedHeader) {
      List<DataRow> rows = new ArrayList<>();
      rowsByFile.put(name, rows);
      byte[] bytes = files.get(name);
      if (bytes == null) {
        fileError("F1", name, "falta el fichero");
        return;
      }
      if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
        fileError("F1", name, "el fichero tiene BOM; debe ser UTF-8 sin BOM");
        return;
      }
      try {
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
                new Issue(
                    Severity.ERROR,
                    "F2",
                    name,
                    1L,
                    null,
                    "cabecera incorrecta; se esperaba " + String.join(",", expectedHeader)));
            return;
          }
          for (CSVRecord record : parser) {
            long line = record.getRecordNumber() + 1;
            if (record.size() != expectedHeader.size()) {
              errors.add(
                  new Issue(
                      Severity.ERROR,
                      "F3",
                      name,
                      line,
                      null,
                      "tiene " + record.size() + " columnas y se esperaban " + expectedHeader.size()));
              continue;
            }
            rows.add(new DataRow(name, line, record.toMap()));
          }
        }
      } catch (CharacterCodingException e) {
        fileError("F1", name, "no es UTF-8 válido");
      } catch (IOException | RuntimeException e) {
        fileError("F2", name, "no se puede leer como CSV (RFC 4180): " + e.getMessage());
      }
    }

    /** F6: ningún texto supera la longitud que admite la base de datos. */
    private void checkLengths() {
      for (List<DataRow> rows : rowsByFile.values()) {
        for (DataRow r : rows) {
          r.values()
              .forEach(
                  (column, value) -> {
                    int max = maxLength(column);
                    if (value != null && value.codePointCount(0, value.length()) > max) {
                      error("F6", r, column, "supera la longitud máxima de " + max + " caracteres");
                    }
                  });
        }
      }
    }

    private int maxLength(String column) {
      if (column.endsWith("_id") || SHORT_COLUMNS.contains(column)) {
        return 64;
      }
      return switch (column) {
        case "title", "help_text" -> 500;
        case "url", "website_url", "condition_text" -> 1000;
        case "notes" -> 2000;
        // source_excerpt tiene su propia validación (F11).
        case "source_excerpt" -> Integer.MAX_VALUE;
        default -> 255;
      };
    }

    // ---------- manifiesto (F1, F6, F7, F8) ----------

    private void checkManifest() {
      byte[] bytes = files.get(MANIFEST);
      if (bytes == null) {
        fileError("F1", MANIFEST, "falta el fichero");
        return;
      }
      JsonNode manifest;
      try {
        manifest = json.readTree(new String(bytes, StandardCharsets.UTF_8));
      } catch (RuntimeException e) {
        fileError("F1", MANIFEST, "no es un JSON válido");
        return;
      }
      String year = text(manifest, "academic_year");
      if (!ACADEMIC_YEAR.matcher(year).matches()) {
        manifestError("F6", "academic_year", "debe tener el formato AAAA-AA");
      } else {
        academicYear = year;
        if (files.directoryName() != null && !year.equals(files.directoryName())) {
          manifestError("F8", "academic_year", "no coincide con el nombre de la carpeta (" + files.directoryName() + ")");
        }
      }
      try {
        manifestDate = LocalDate.parse(text(manifest, "date"));
      } catch (DateTimeParseException e) {
        manifestError("F6", "date", "debe ser una fecha AAAA-MM-DD");
      }
      reviewer = text(manifest, "reviewer");
      if (reviewer.isBlank()) {
        manifestError("F7", "reviewer", "es obligatorio");
      }
      Map<String, String> checksums = new HashMap<>();
      for (JsonNode file : manifest.path("files")) {
        checksums.put(text(file, "name"), text(file, "sha256"));
      }
      if (!checksums.keySet().equals(DatasetContract.HEADERS.keySet())) {
        manifestError("F8", "files", "debe listar exactamente los 9 ficheros del contrato");
      }
      for (String name : DatasetContract.HEADERS.keySet()) {
        byte[] content = files.get(name);
        if (content != null && checksums.containsKey(name) && !sha256(content).equals(checksums.get(name))) {
          manifestError("F8", "files", "el checksum de " + name + " no coincide (real: " + sha256(content) + ")");
        }
      }
    }

    // ---------- contenido ----------

    private void checkContent() {
      Map<String, DataRow> universities = keyed(UNIVERSITIES, "university_id");
      Map<String, DataRow> cycles = keyed(CYCLES, "cycle_id");
      Map<String, DataRow> modules = keyed(MODULES, "module_id");
      Map<String, DataRow> degrees = keyed(DEGREES, "degree_id");
      Map<String, DataRow> subjects = keyed(SUBJECTS, "subject_id");
      Map<String, DataRow> sources = keyed(SOURCES, "source_id");
      Map<String, DataRow> tables = keyed(TABLES, "table_id");
      Map<String, DataRow> rules = keyed(RULES, "rule_id");

      for (DataRow r : universities.values()) {
        required(r, "name");
        oneOf(r, "ownership", "PUBLICA", "PRIVADA");
      }
      for (DataRow r : cycles.values()) {
        required(r, "name");
        ects(r, "total_ects");
      }
      for (DataRow r : modules.values()) {
        required(r, "name");
        foreignKey(r, "cycle_id", cycles, true);
        ects(r, "ects");
        positiveInt(r, "course_year");
      }
      for (DataRow r : degrees.values()) {
        required(r, "name");
        foreignKey(r, "university_id", universities, true);
        oneOf(r, "degree_type", "SIMPLE", "DOBLE");
        ects(r, "total_ects");
        if (!r.blank("subjects_complete")) {
          oneOf(r, "subjects_complete", "true", "false");
        }
      }
      for (DataRow r : subjects.values()) {
        required(r, "name");
        foreignKey(r, "degree_id", degrees, true);
        ects(r, "ects");
        oneOf(r, "subject_type", "BASICA", "OBLIGATORIA", "OPTATIVA", "PRACTICAS_EXTERNAS", "TFG");
        positiveInt(r, "course_year");
      }
      for (DataRow r : sources.values()) {
        required(r, "title");
        oneOf(r, "document_type", "CONVENIO", "ADENDA", "TABLA_UNIVERSIDAD", "BOLETIN_OFICIAL", "OTRO");
        date(r, "publication_date");
        date(r, "retrieved_at");
        if (!r.blank("file_sha256") && !SHA256.matcher(r.get("file_sha256")).matches()) {
          error("F6", r, "file_sha256", "debe ser un SHA-256 en hexadecimal y minúsculas");
        }
      }

      checkTables(tables, cycles, degrees, sources);
      Map<String, Set<String>> modulesByRule = checkRequirements(rules, modules, tables);
      checkRules(rules, tables, subjects, sources, modulesByRule);
    }

    private void checkTables(
        Map<String, DataRow> tables,
        Map<String, DataRow> cycles,
        Map<String, DataRow> degrees,
        Map<String, DataRow> sources) {
      Set<String> combinations = new HashSet<>();
      Set<String> degreesReported = new HashSet<>();
      for (DataRow r : tables.values()) {
        foreignKey(r, "cycle_id", cycles, true);
        foreignKey(r, "degree_id", degrees, true);
        foreignKey(r, "source_id", sources, false);
        oneOf(r, "status", "VIGENTE", "SIN_CONVENIO");
        ects(r, "table_max_ects");
        if (academicYear != null && !academicYear.equals(r.get("academic_year"))) {
          error("F8", r, "academic_year", "debe coincidir con el curso del manifiesto (" + academicYear + ")");
        }
        if (!combinations.add(r.get("cycle_id") + "|" + r.get("degree_id") + "|" + r.get("academic_year"))) {
          error("V9", r, "table_id", "ya existe otra tabla para el mismo ciclo, grado y curso");
        }
        if ("VIGENTE".equals(r.get("status"))) {
          if (r.blank("source_id")) {
            error("V6", r, "source_id", "una tabla VIGENTE debe tener documento fuente");
          }
          DataRow degree = degrees.get(r.get("degree_id"));
          if (degree != null && degree.blank("total_ects") && degreesReported.add(r.get("degree_id"))) {
            error("V11", degree, "total_ects", "es obligatorio en un grado con tabla VIGENTE (sin él no hay tope)");
          }
        }
      }
    }

    private Map<String, Set<String>> checkRequirements(
        Map<String, DataRow> rules, Map<String, DataRow> modules, Map<String, DataRow> tables) {
      Map<String, Set<String>> modulesByRule = new HashMap<>();
      Set<String> keys = new HashSet<>();
      for (DataRow r : rowsByFile.get(REQUIREMENTS)) {
        foreignKey(r, "rule_id", rules, true);
        foreignKey(r, "module_id", modules, true);
        if (!keys.add(r.get("rule_id") + "|" + r.get("module_id"))) {
          error("V2", r, "module_id", "requisito duplicado para la misma regla");
        }
        modulesByRule.computeIfAbsent(r.get("rule_id"), k -> new TreeSet<>()).add(r.get("module_id"));
        DataRow rule = rules.get(r.get("rule_id"));
        DataRow table = rule == null ? null : tables.get(rule.get("table_id"));
        DataRow module = modules.get(r.get("module_id"));
        if (table != null && module != null && !table.get("cycle_id").equals(module.get("cycle_id"))) {
          error("V5", r, "module_id", "el módulo no pertenece al ciclo de la tabla de la regla");
        }
      }
      return modulesByRule;
    }

    private void checkRules(
        Map<String, DataRow> rules,
        Map<String, DataRow> tables,
        Map<String, DataRow> subjects,
        Map<String, DataRow> sources,
        Map<String, Set<String>> modulesByRule) {
      Set<String> tablesWithRules = new HashSet<>();
      Set<String> signatures = new HashSet<>();
      Set<String> subjectsReported = new HashSet<>();
      // Por tabla y asignatura, los ECTS que reconoce la primera regla no condicionada (para A3).
      Map<String, BigDecimal> ectsByTarget = new HashMap<>();

      for (DataRow r : rules.values()) {
        foreignKey(r, "table_id", tables, true);
        foreignKey(r, "source_id", sources, false);
        oneOf(r, "target_type", "SUBJECT", "OPTATIVE_CREDITS");
        oneOf(r, "is_conditional", "true", "false");
        boolean ectsOk = ects(r, "recognized_ects");
        if (r.blank("source_page")) {
          error("V8", r, "source_page", "toda regla debe indicar la página de su fuente");
        } else {
          positiveInt(r, "source_page");
        }
        tablesWithRules.add(r.get("table_id"));
        DataRow table = tables.get(r.get("table_id"));

        if ("SUBJECT".equals(r.get("target_type"))) {
          foreignKey(r, "target_subject_id", subjects, true);
          DataRow subject = subjects.get(r.get("target_subject_id"));
          if (table != null && subject != null && !table.get("degree_id").equals(subject.get("degree_id"))) {
            error("V4", r, "target_subject_id", "la asignatura no pertenece al grado de la tabla");
          }
          if (subject != null && r.blank("recognized_ects") && subject.blank("ects")) {
            if (subjectsReported.add(r.get("target_subject_id"))) {
              error("V12", subject, "ects", "es obligatorio en la asignatura destino de una regla sin recognized_ects");
            }
          } else if (subject != null && ectsOk && "false".equals(r.get("is_conditional"))) {
            warnIfAlternativeEctsDiffer(r, subject, ectsByTarget);
          }
        } else if ("OPTATIVE_CREDITS".equals(r.get("target_type"))) {
          if (!r.blank("target_subject_id")) {
            error("F9", r, "target_subject_id", "debe estar vacío en una regla OPTATIVE_CREDITS");
          }
          if (r.blank("recognized_ects")) {
            error("F9", r, "recognized_ects", "es obligatorio en una regla OPTATIVE_CREDITS");
          }
        }
        if (r.get("source_excerpt").codePointCount(0, r.get("source_excerpt").length()) > MAX_EXCERPT) {
          error("F11", r, "source_excerpt", "supera los " + MAX_EXCERPT + " caracteres");
        }
        if (table != null && "SIN_CONVENIO".equals(table.get("status"))) {
          error("F10", r, "table_id", "una tabla SIN_CONVENIO no puede tener reglas");
        }
        Set<String> ruleModules = modulesByRule.getOrDefault(r.get("rule_id"), Set.of());
        String signature =
            r.get("table_id") + "|" + r.get("target_type") + "|" + r.get("target_subject_id") + "|" + ruleModules;
        if (!signatures.add(signature)) {
          error("V10", r, "rule_id", "regla duplicada: mismo destino y mismo conjunto de módulos");
        }
        if ("true".equals(r.get("is_conditional")) && r.blank("condition_text")) {
          warning("A1", r, "condition_text", "regla condicionada sin condición especificada en el documento");
        }
        if (ruleModules.isEmpty()) {
          warning("A2", r, "rule_id", "regla sin módulos (reconocimiento por tener el título): revisar a mano");
        }
      }
      for (DataRow r : tables.values()) {
        if ("VIGENTE".equals(r.get("status")) && !tablesWithRules.contains(r.get("table_id"))) {
          error("V7", r, "table_id", "una tabla VIGENTE debe tener al menos una regla");
        }
      }
    }

    /** A3: dos reglas alternativas llevan a la misma asignatura con ECTS distintos. */
    private void warnIfAlternativeEctsDiffer(DataRow rule, DataRow subject, Map<String, BigDecimal> ectsByTarget) {
      String raw = rule.blank("recognized_ects") ? subject.get("ects") : rule.get("recognized_ects");
      if (!DECIMAL.matcher(raw).matches()) {
        return;
      }
      BigDecimal ects = new BigDecimal(raw);
      BigDecimal first = ectsByTarget.putIfAbsent(rule.get("table_id") + "|" + rule.get("target_subject_id"), ects);
      if (first != null && first.compareTo(ects) != 0) {
        warning(
            "A3",
            rule,
            "recognized_ects",
            "otra regla reconoce la misma asignatura con "
                + first.stripTrailingZeros().toPlainString()
                + " ECTS y esta con "
                + ects.stripTrailingZeros().toPlainString()
                + "; cuentan los de la regla de menor rule_id");
      }
    }

    // ---------- comprobaciones de celda ----------

    /** Indexa un fichero por su clave: formato del identificador (F4) y unicidad (V2). */
    private Map<String, DataRow> keyed(String file, String keyColumn) {
      Map<String, DataRow> byKey = new LinkedHashMap<>();
      for (DataRow r : rowsByFile.get(file)) {
        String key = r.get(keyColumn);
        if (!ID.matcher(key).matches()) {
          error("F4", r, keyColumn, "identificador vacío o con formato no válido (mayúsculas, cifras y guiones)");
        } else if (byKey.putIfAbsent(key, r) != null) {
          error("V2", r, keyColumn, "clave duplicada: " + key);
        }
      }
      return byKey;
    }

    private void required(DataRow r, String column) {
      if (r.blank(column)) {
        error("F7", r, column, "es obligatorio");
      }
    }

    private void oneOf(DataRow r, String column, String... allowed) {
      if (!Set.of(allowed).contains(r.get(column))) {
        error("F5", r, column, "valor no permitido; debe ser uno de " + String.join(", ", allowed));
      }
    }

    private void foreignKey(DataRow r, String column, Map<String, DataRow> target, boolean mandatory) {
      if (r.blank(column)) {
        if (mandatory) {
          error("F7", r, column, "es obligatorio");
        }
      } else if (!target.containsKey(r.get(column))) {
        error("V1", r, column, "referencia a un identificador que no existe: " + r.get(column));
      }
    }

    /** Celda vacía = desconocido (permitido). Devuelve false si el valor no es válido. */
    private boolean ects(DataRow r, String column) {
      if (r.blank(column)) {
        return true;
      }
      if (!DECIMAL.matcher(r.get(column)).matches()) {
        error("F6", r, column, "debe ser un número con punto decimal y como máximo 2 decimales");
        return false;
      }
      if (new BigDecimal(r.get(column)).signum() <= 0) {
        error("V3", r, column, "los ECTS deben ser mayores que 0");
        return false;
      }
      return true;
    }

    private void positiveInt(DataRow r, String column) {
      if (!r.blank(column) && !POSITIVE_INT.matcher(r.get(column)).matches()) {
        error("F6", r, column, "debe ser un entero mayor que 0");
      }
    }

    private void date(DataRow r, String column) {
      if (r.blank(column)) {
        return;
      }
      try {
        LocalDate.parse(r.get(column));
      } catch (DateTimeParseException e) {
        error("F6", r, column, "debe ser una fecha AAAA-MM-DD");
      }
    }

    private void error(String code, DataRow r, String column, String message) {
      errors.add(new Issue(Severity.ERROR, code, r.file(), r.line(), column, message));
    }

    private void warning(String code, DataRow r, String column, String message) {
      warnings.add(new Issue(Severity.WARNING, code, r.file(), r.line(), column, message));
    }

    private void fileError(String code, String file, String message) {
      errors.add(new Issue(Severity.ERROR, code, file, null, null, message));
    }

    private void manifestError(String code, String field, String message) {
      errors.add(new Issue(Severity.ERROR, code, MANIFEST, null, field, message));
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isString() ? value.asString() : "";
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
