package es.convalidafp.ingestion;

import static es.convalidafp.ingestion.DatasetContract.CYCLES;
import static es.convalidafp.ingestion.DatasetContract.DEGREES;
import static es.convalidafp.ingestion.DatasetContract.MODULES;
import static es.convalidafp.ingestion.DatasetContract.REQUIREMENTS;
import static es.convalidafp.ingestion.DatasetContract.RULES;
import static es.convalidafp.ingestion.DatasetContract.SOURCES;
import static es.convalidafp.ingestion.DatasetContract.SUBJECTS;
import static es.convalidafp.ingestion.DatasetContract.TABLES;
import static es.convalidafp.ingestion.DatasetContract.UNIVERSITIES;

import es.convalidafp.ingestion.RawDataset.DataRow;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Foto comparable de un dataset: por fichero, cada fila por su identificador, con las columnas del
 * contrato como texto normalizado (los decimales sin ceros de relleno, vacío = desconocido). Un
 * dataset recién leído y una versión guardada producen la misma foto si su contenido es el mismo.
 */
final class DatasetSnapshot {

  /** Ficheros con identificador propio. Los requisitos se pliegan en la regla. */
  static final List<String> FILES = List.of(UNIVERSITIES, CYCLES, MODULES, DEGREES, SUBJECTS, SOURCES, TABLES, RULES);

  private static final Set<String> DECIMAL_COLUMNS = Set.of("total_ects", "ects", "table_max_ects", "recognized_ects");

  private final Map<String, Map<String, Map<String, String>>> rowsByFile = new LinkedHashMap<>();

  DatasetSnapshot() {
    FILES.forEach(file -> rowsByFile.put(file, new TreeMap<>()));
  }

  static DatasetSnapshot empty() {
    return new DatasetSnapshot();
  }

  /** Foto de un dataset leído de sus CSV. */
  static DatasetSnapshot of(RawDataset data) {
    DatasetSnapshot snapshot = new DatasetSnapshot();
    Map<String, Set<String>> modulesByRule = new TreeMap<>();
    for (DataRow r : data.rows(REQUIREMENTS)) {
      modulesByRule.computeIfAbsent(r.get("rule_id"), k -> new TreeSet<>()).add(r.get("module_id"));
    }
    for (String file : FILES) {
      List<String> header = DatasetContract.HEADERS.get(file);
      for (DataRow r : data.rows(file)) {
        Map<String, String> values = new LinkedHashMap<>();
        header.stream().skip(1).forEach(column -> values.put(column, r.get(column)));
        if (file.equals(RULES)) {
          values.put(DatasetDiff.REQUIRED_MODULES, joinModules(modulesByRule.getOrDefault(r.get(header.get(0)), Set.of())));
        }
        snapshot.put(file, r.get(header.get(0)), values);
      }
    }
    return snapshot;
  }

  /** Añade una fila. Los decimales se normalizan para que 6, 6.0 y 6.00 sean el mismo valor. */
  void put(String file, String id, Map<String, String> values) {
    Map<String, String> normalized = new LinkedHashMap<>();
    values.forEach((column, value) -> normalized.put(column, normalize(column, value)));
    rowsByFile.get(file).put(id, normalized);
  }

  Map<String, Map<String, String>> rows(String file) {
    return rowsByFile.get(file);
  }

  static String joinModules(Set<String> moduleIds) {
    return String.join(" + ", new TreeSet<>(moduleIds));
  }

  private static String normalize(String column, String value) {
    // subjects_complete: vacío = false (CONTRACT.md, 3.4).
    if ("subjects_complete".equals(column) && (value == null || value.isEmpty())) {
      return "false";
    }
    if (value == null || value.isEmpty()) {
      return "";
    }
    if (DECIMAL_COLUMNS.contains(column)) {
      return new BigDecimal(value).stripTrailingZeros().toPlainString();
    }
    return value;
  }
}
