package es.convalidafp.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Diferencias entre un dataset nuevo y la versión con la que se compara: altas, bajas y cambios,
 * fichero a fichero. Las filas se emparejan por su identificador, que es estable entre cursos.
 *
 * @param baselineVersionId versión con la que se compara; null si no hay ninguna publicada (todo
 *     son altas)
 * @param baselineAcademicYear curso de esa versión; puede ser un curso anterior al del dataset
 */
public record DatasetDiff(Long baselineVersionId, String baselineAcademicYear, List<FileDiff> files) {

  /**
   * Columna añadida a las reglas para comparar sus módulos: rule_requirements.csv no tiene
   * identificador propio, así que sus cambios se muestran como cambios de la regla.
   */
  public static final String REQUIRED_MODULES = "required_modules";

  public DatasetDiff {
    files = List.copyOf(files);
  }

  /** Diferencias de un fichero. */
  public record FileDiff(String file, List<String> added, List<String> removed, List<RowChange> changed) {

    public FileDiff {
      added = List.copyOf(added);
      removed = List.copyOf(removed);
      changed = List.copyOf(changed);
    }

    public boolean isEmpty() {
      return added.isEmpty() && removed.isEmpty() && changed.isEmpty();
    }
  }

  /** Una fila que existe en ambos lados y ha cambiado. */
  public record RowChange(String id, List<FieldChange> fields) {

    public RowChange {
      fields = List.copyOf(fields);
    }
  }

  /** Un valor que cambia. Cadena vacía = desconocido. */
  public record FieldChange(String column, String before, String after) {}

  /** No hay ninguna diferencia. */
  public boolean isEmpty() {
    return files.stream().allMatch(FileDiff::isEmpty);
  }

  public FileDiff file(String name) {
    return files.stream().filter(f -> f.file().equals(name)).findFirst().orElseThrow();
  }

  /**
   * Compara dos fotos de dataset. El curso de las tablas no se compara: cambia siempre de un curso
   * al siguiente y no es una diferencia de contenido.
   */
  static DatasetDiff between(Long baselineVersionId, String baselineAcademicYear, DatasetSnapshot before, DatasetSnapshot after) {
    List<FileDiff> files = new ArrayList<>();
    for (String file : DatasetSnapshot.FILES) {
      Map<String, Map<String, String>> oldRows = before.rows(file);
      Map<String, Map<String, String>> newRows = after.rows(file);
      List<String> added = new ArrayList<>();
      List<String> removed = new ArrayList<>();
      List<RowChange> changed = new ArrayList<>();
      Set<String> ids = new TreeSet<>(oldRows.keySet());
      ids.addAll(newRows.keySet());
      for (String id : ids) {
        if (!oldRows.containsKey(id)) {
          added.add(id);
        } else if (!newRows.containsKey(id)) {
          removed.add(id);
        } else {
          List<FieldChange> fields = new ArrayList<>();
          newRows.get(id).forEach((column, value) -> {
            String old = oldRows.get(id).getOrDefault(column, "");
            if (!column.equals("academic_year") && !Objects.equals(old, value)) {
              fields.add(new FieldChange(column, old, value));
            }
          });
          if (!fields.isEmpty()) {
            changed.add(new RowChange(id, fields));
          }
        }
      }
      files.add(new FileDiff(file, added, removed, changed));
    }
    return new DatasetDiff(baselineVersionId, baselineAcademicYear, files);
  }
}
