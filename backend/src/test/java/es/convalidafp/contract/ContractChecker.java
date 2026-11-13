package es.convalidafp.contract;

import es.convalidafp.contract.Dataset.Row;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Comprueba un dataset contra las reglas de CONTRACT.md. Devuelve errores (bloquean la carga) y
 * avisos (no la bloquean; se revisan a mano). Cada uno indica fichero, fila, columna y motivo.
 *
 * <p>Código de test de la Fase 0; el importador de la Fase 3 implementa estas mismas reglas.
 */
final class ContractChecker {

  private static final Pattern ID = Pattern.compile("[A-Z0-9]+(-[A-Z0-9]+)*");
  private static final Pattern ACADEMIC_YEAR = Pattern.compile("\\d{4}-\\d{2}");
  private static final Pattern DECIMAL = Pattern.compile("\\d+(\\.\\d{1,2})?");
  private static final Pattern POSITIVE_INT = Pattern.compile("[1-9]\\d*");
  private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
  private static final int MAX_EXCERPT = 200;

  /** Resultado de la comprobación: los errores bloquean la carga; los avisos, no. */
  record Report(List<String> errors, List<String> warnings) {}

  private final Dataset data;
  private final List<String> errors;
  private final List<String> warnings = new ArrayList<>();

  private ContractChecker(Dataset data) {
    this.data = data;
    this.errors = new ArrayList<>(data.errors);
  }

  static Report check(Dataset data) {
    ContractChecker checker = new ContractChecker(data);
    checker.run();
    return new Report(List.copyOf(checker.errors), List.copyOf(checker.warnings));
  }

  private void run() {
    String year = checkManifest();

    Map<String, Row> universities = keyed("universities.csv", "university_id");
    Map<String, Row> cycles = keyed("fp_cycles.csv", "cycle_id");
    Map<String, Row> modules = keyed("fp_modules.csv", "module_id");
    Map<String, Row> degrees = keyed("degrees.csv", "degree_id");
    Map<String, Row> subjects = keyed("degree_subjects.csv", "subject_id");
    Map<String, Row> sources = keyed("source_documents.csv", "source_id");
    Map<String, Row> tables = keyed("recognition_tables.csv", "table_id");
    Map<String, Row> rules = keyed("recognition_rules.csv", "rule_id");

    for (Row r : universities.values()) {
      required(r, "name");
      oneOf(r, "ownership", "PUBLICA", "PRIVADA");
    }
    for (Row r : cycles.values()) {
      required(r, "name");
      ects(r, "total_ects");
    }
    for (Row r : modules.values()) {
      required(r, "name");
      foreignKey(r, "cycle_id", cycles, true);
      ects(r, "ects");
      positiveInt(r, "course_year");
    }
    for (Row r : degrees.values()) {
      required(r, "name");
      foreignKey(r, "university_id", universities, true);
      oneOf(r, "degree_type", "SIMPLE", "DOBLE");
      ects(r, "total_ects");
    }
    for (Row r : subjects.values()) {
      required(r, "name");
      foreignKey(r, "degree_id", degrees, true);
      ects(r, "ects");
      oneOf(r, "subject_type", "BASICA", "OBLIGATORIA", "OPTATIVA", "PRACTICAS_EXTERNAS", "TFG");
      positiveInt(r, "course_year");
    }
    for (Row r : sources.values()) {
      required(r, "title");
      oneOf(
          r, "document_type", "CONVENIO", "ADENDA", "TABLA_UNIVERSIDAD", "BOLETIN_OFICIAL", "OTRO");
      date(r, "publication_date");
      date(r, "retrieved_at");
      if (!r.blank("file_sha256") && !SHA256.matcher(r.get("file_sha256")).matches()) {
        error(r, "file_sha256", "debe ser un SHA-256 en hexadecimal y minúsculas");
      }
    }

    Set<String> reported = new HashSet<>();
    Set<String> combinations = new HashSet<>();
    for (Row r : tables.values()) {
      foreignKey(r, "cycle_id", cycles, true);
      foreignKey(r, "degree_id", degrees, true);
      foreignKey(r, "source_id", sources, false);
      oneOf(r, "status", "VIGENTE", "SIN_CONVENIO");
      ects(r, "table_max_ects");
      if (year != null && !year.equals(r.get("academic_year"))) {
        error(r, "academic_year", "debe coincidir con el curso del manifiesto (" + year + ")");
      }
      String combination = r.get("cycle_id") + "|" + r.get("degree_id") + "|" + r.get("academic_year");
      if (!combinations.add(combination)) {
        error(r, "table_id", "ya existe otra tabla para el mismo ciclo, grado y curso");
      }
      if ("VIGENTE".equals(r.get("status")) && r.blank("source_id")) {
        error(r, "source_id", "una tabla VIGENTE debe tener documento fuente");
      }
      Row degree = degrees.get(r.get("degree_id"));
      if ("VIGENTE".equals(r.get("status"))
          && degree != null
          && degree.blank("total_ects")
          && reported.add("degree|" + r.get("degree_id"))) {
        error(degree, "total_ects", "es obligatorio en un grado con tabla VIGENTE (sin él no hay tope)");
      }
    }

    Map<String, Set<String>> modulesByRule = new HashMap<>();
    Set<String> requirementKeys = new HashSet<>();
    for (Row r : data.rows("rule_requirements.csv")) {
      foreignKey(r, "rule_id", rules, true);
      foreignKey(r, "module_id", modules, true);
      if (!requirementKeys.add(r.get("rule_id") + "|" + r.get("module_id"))) {
        error(r, "module_id", "requisito duplicado para la misma regla");
      }
      modulesByRule.computeIfAbsent(r.get("rule_id"), k -> new TreeSet<>()).add(r.get("module_id"));
      Row table = tables.get(rules.getOrDefault(r.get("rule_id"), r).get("table_id"));
      Row module = modules.get(r.get("module_id"));
      if (table != null && module != null && !table.get("cycle_id").equals(module.get("cycle_id"))) {
        error(r, "module_id", "el módulo no pertenece al ciclo de la tabla de la regla");
      }
    }

    Set<String> tablesWithRules = new HashSet<>();
    Set<String> ruleSignatures = new HashSet<>();
    for (Row r : rules.values()) {
      foreignKey(r, "table_id", tables, true);
      foreignKey(r, "source_id", sources, false);
      oneOf(r, "target_type", "SUBJECT", "OPTATIVE_CREDITS");
      oneOf(r, "is_conditional", "true", "false");
      ects(r, "recognized_ects");
      required(r, "source_page");
      positiveInt(r, "source_page");
      tablesWithRules.add(r.get("table_id"));
      Row table = tables.get(r.get("table_id"));

      if ("SUBJECT".equals(r.get("target_type"))) {
        foreignKey(r, "target_subject_id", subjects, true);
        Row subject = subjects.get(r.get("target_subject_id"));
        if (table != null && subject != null && !table.get("degree_id").equals(subject.get("degree_id"))) {
          error(r, "target_subject_id", "la asignatura no pertenece al grado de la tabla");
        }
        if (subject != null
            && r.blank("recognized_ects")
            && subject.blank("ects")
            && reported.add("subject|" + r.get("target_subject_id"))) {
          error(subject, "ects", "es obligatorio en la asignatura destino de una regla sin recognized_ects");
        }
      } else if ("OPTATIVE_CREDITS".equals(r.get("target_type"))) {
        if (!r.blank("target_subject_id")) {
          error(r, "target_subject_id", "debe estar vacío en una regla OPTATIVE_CREDITS");
        }
        required(r, "recognized_ects");
      }
      if ("true".equals(r.get("is_conditional")) && r.blank("condition_text")) {
        warning(r, "condition_text", "regla condicionada sin condición especificada en el documento");
      }
      if (!modulesByRule.containsKey(r.get("rule_id"))) {
        warning(r, "rule_id", "regla sin módulos (reconocimiento por tener el título): revisar a mano");
      }
      if (r.get("source_excerpt").codePointCount(0, r.get("source_excerpt").length()) > MAX_EXCERPT) {
        error(r, "source_excerpt", "supera los " + MAX_EXCERPT + " caracteres");
      }
      if (table != null && "SIN_CONVENIO".equals(table.get("status"))) {
        error(r, "table_id", "una tabla SIN_CONVENIO no puede tener reglas");
      }
      String signature =
          r.get("table_id")
              + "|"
              + r.get("target_type")
              + "|"
              + r.get("target_subject_id")
              + "|"
              + modulesByRule.getOrDefault(r.get("rule_id"), Set.of());
      if (!ruleSignatures.add(signature)) {
        error(r, "rule_id", "regla duplicada: mismo destino y mismo conjunto de módulos");
      }
    }
    for (Row r : tables.values()) {
      if ("VIGENTE".equals(r.get("status")) && !tablesWithRules.contains(r.get("table_id"))) {
        error(r, "table_id", "una tabla VIGENTE debe tener al menos una regla");
      }
    }
  }

  /** Valida manifest.json y devuelve el curso declarado, o null si no se puede leer. */
  private String checkManifest() {
    Path path = data.dir.resolve("manifest.json");
    if (!Files.isRegularFile(path)) {
      errors.add("manifest.json: falta el fichero");
      return null;
    }
    try {
      JsonNode manifest = new ObjectMapper().readTree(Files.readString(path));
      String year = text(manifest, "academic_year");
      if (!ACADEMIC_YEAR.matcher(year).matches()) {
        errors.add("manifest.json: academic_year debe tener el formato AAAA-AA");
      } else if (!year.equals(data.dir.getFileName().toString())) {
        errors.add("manifest.json: academic_year no coincide con el nombre de la carpeta");
      }
      try {
        LocalDate.parse(text(manifest, "date"));
      } catch (DateTimeParseException e) {
        errors.add("manifest.json: date debe ser una fecha AAAA-MM-DD");
      }
      if (text(manifest, "reviewer").isBlank()) {
        errors.add("manifest.json: falta reviewer");
      }
      Map<String, String> checksums = new HashMap<>();
      for (JsonNode file : manifest.path("files")) {
        checksums.put(text(file, "name"), text(file, "sha256"));
      }
      if (!checksums.keySet().equals(Dataset.HEADERS.keySet())) {
        errors.add("manifest.json: files debe listar exactamente los 9 ficheros del contrato");
      }
      for (String name : Dataset.HEADERS.keySet()) {
        Path file = data.dir.resolve(name);
        if (Files.isRegularFile(file) && checksums.containsKey(name)) {
          String actual = sha256(file);
          if (!actual.equals(checksums.get(name))) {
            errors.add("manifest.json: el checksum de " + name + " no coincide (real: " + actual + ")");
          }
        }
      }
      return year;
    } catch (IOException | RuntimeException e) {
      errors.add("manifest.json: no se puede leer: " + e.getMessage());
      return null;
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isString() ? value.asString() : "";
  }

  private static String sha256(Path file) throws IOException {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Indexa un fichero por su clave y comprueba formato y unicidad de los identificadores. */
  private Map<String, Row> keyed(String file, String keyColumn) {
    Map<String, Row> byKey = new java.util.LinkedHashMap<>();
    for (Row r : data.rows(file)) {
      String key = r.get(keyColumn);
      if (!ID.matcher(key).matches()) {
        error(r, keyColumn, "identificador vacío o con formato no válido (mayúsculas, cifras y guiones)");
      } else if (byKey.putIfAbsent(key, r) != null) {
        error(r, keyColumn, "clave duplicada: " + key);
      }
    }
    return byKey;
  }

  private void required(Row r, String column) {
    if (r.blank(column)) {
      error(r, column, "es obligatorio");
    }
  }

  private void oneOf(Row r, String column, String... allowed) {
    if (!Set.of(allowed).contains(r.get(column))) {
      error(r, column, "valor no permitido; debe ser uno de " + String.join(", ", allowed));
    }
  }

  private void foreignKey(Row r, String column, Map<String, Row> target, boolean mandatory) {
    if (r.blank(column)) {
      if (mandatory) {
        error(r, column, "es obligatorio");
      }
    } else if (!target.containsKey(r.get(column))) {
      error(r, column, "referencia a un identificador que no existe: " + r.get(column));
    }
  }

  /** Celda vacía = desconocido (permitido). Si hay valor, debe ser decimal con punto y mayor que 0. */
  private void ects(Row r, String column) {
    if (r.blank(column)) {
      return;
    }
    if (!DECIMAL.matcher(r.get(column)).matches()) {
      error(r, column, "debe ser un número con punto decimal y como máximo 2 decimales");
    } else if (new BigDecimal(r.get(column)).signum() <= 0) {
      error(r, column, "los ECTS deben ser mayores que 0");
    }
  }

  private void positiveInt(Row r, String column) {
    if (!r.blank(column) && !POSITIVE_INT.matcher(r.get(column)).matches()) {
      error(r, column, "debe ser un entero mayor que 0");
    }
  }

  private void date(Row r, String column) {
    if (r.blank(column)) {
      return;
    }
    try {
      LocalDate.parse(r.get(column));
    } catch (DateTimeParseException e) {
      error(r, column, "debe ser una fecha AAAA-MM-DD");
    }
  }

  private void error(Row r, String column, String reason) {
    errors.add(r.file() + ": fila " + r.line() + ": columna " + column + ": " + reason);
  }

  private void warning(Row r, String column, String reason) {
    warnings.add(r.file() + ": fila " + r.line() + ": columna " + column + ": " + reason);
  }

  /** Solo para depuración en los mensajes de los tests. */
  static String describe(List<String> errors) {
    return errors.stream().collect(Collectors.joining("\n"));
  }
}
