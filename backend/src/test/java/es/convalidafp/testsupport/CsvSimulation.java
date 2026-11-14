package es.convalidafp.testsupport;

import es.convalidafp.contract.Dataset;
import es.convalidafp.contract.Dataset.Row;
import es.convalidafp.simulation.DegreeData;
import es.convalidafp.simulation.DegreeData.SubjectData;
import es.convalidafp.simulation.RuleData;
import es.convalidafp.simulation.SimulationEngine;
import es.convalidafp.simulation.SimulationInput;
import es.convalidafp.simulation.SimulationResult;
import es.convalidafp.simulation.SourceRef;
import es.convalidafp.simulation.TableData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simula con el motor a partir de los CSV del dataset de ejemplo, sin pasar por la base de datos.
 * Es la referencia contra la que se comparan el motor y la API.
 */
public final class CsvSimulation {

  private CsvSimulation() {}

  /**
   * @param modules módulos superados; null = el estudiante tiene el título
   */
  public static SimulationResult simulate(
      SimulationEngine engine, Dataset data, String year, String cycleId, String degreeId, Set<String> modules) {
    SimulationInput input = new SimulationInput(cycleId, degreeId, year, 1L, modules);
    Map<String, SourceRef> sources = new HashMap<>();
    for (Row r : data.rows("source_documents.csv")) {
      LocalDate date = r.blank("publication_date") ? null : LocalDate.parse(r.get("publication_date"));
      sources.put(
          r.get("source_id"),
          new SourceRef(r.get("source_id"), r.get("document_type"), r.get("title"), date, text(r, "url")));
    }
    Row degreeRow =
        data.rows("degrees.csv").stream().filter(r -> r.get("degree_id").equals(degreeId)).findFirst().orElseThrow();
    List<SubjectData> subjects =
        data.rows("degree_subjects.csv").stream()
            .filter(r -> r.get("degree_id").equals(degreeId))
            .map(
                r ->
                    new SubjectData(
                        r.get("subject_id"),
                        r.get("name"),
                        decimal(r, "ects"),
                        r.get("subject_type"),
                        r.blank("course_year") ? null : Integer.valueOf(r.get("course_year"))))
            .toList();
    DegreeData degree = new DegreeData(degreeId, degreeRow.get("name"), decimal(degreeRow, "total_ects"), subjects);

    Row tableRow =
        data.rows("recognition_tables.csv").stream()
            .filter(r -> r.get("cycle_id").equals(cycleId) && r.get("degree_id").equals(degreeId))
            .findFirst()
            .orElse(null);
    if (tableRow == null) {
      return engine.simulate(input, null, List.of(), degree);
    }
    TableData table =
        new TableData(
            tableRow.get("table_id"),
            TableData.Status.valueOf(tableRow.get("status")),
            sources.get(tableRow.get("source_id")),
            decimal(tableRow, "table_max_ects"),
            text(tableRow, "notes"));

    Map<String, Set<String>> requirements = new HashMap<>();
    for (Row r : data.rows("rule_requirements.csv")) {
      requirements.computeIfAbsent(r.get("rule_id"), k -> new HashSet<>()).add(r.get("module_id"));
    }
    List<RuleData> rules =
        data.rows("recognition_rules.csv").stream()
            .filter(r -> r.get("table_id").equals(table.id()))
            .map(
                r ->
                    new RuleData(
                        r.get("rule_id"),
                        RuleData.TargetType.valueOf(r.get("target_type")),
                        text(r, "target_subject_id"),
                        decimal(r, "recognized_ects"),
                        Boolean.parseBoolean(r.get("is_conditional")),
                        text(r, "condition_text"),
                        sources.get(r.get("source_id")),
                        Integer.parseInt(r.get("source_page")),
                        text(r, "source_excerpt"),
                        requirements.getOrDefault(r.get("rule_id"), Set.of())))
            .toList();
    return engine.simulate(input, table, rules, degree);
  }

  private static String text(Row r, String column) {
    return r.blank(column) ? null : r.get(column);
  }

  private static BigDecimal decimal(Row r, String column) {
    return r.blank(column) ? null : new BigDecimal(r.get(column));
  }
}
