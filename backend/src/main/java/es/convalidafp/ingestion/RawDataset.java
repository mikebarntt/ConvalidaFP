package es.convalidafp.ingestion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Un dataset leído de sus CSV, todavía como texto: filas por fichero más los datos del manifiesto.
 * Solo es fiable si el informe de validación que lo acompaña no tiene errores.
 */
public record RawDataset(
    String academicYear, LocalDate manifestDate, String reviewer, Map<String, List<DataRow>> rowsByFile) {

  public List<DataRow> rows(String file) {
    return rowsByFile.getOrDefault(file, List.of());
  }

  /** Una fila de datos. {@code line} es el número de fila en el fichero (la cabecera es la 1). */
  public record DataRow(String file, long line, Map<String, String> values) {

    public String get(String column) {
      String value = values.get(column);
      return value == null ? "" : value;
    }

    /** Celda vacía = desconocido. */
    public boolean blank(String column) {
      return get(column).isEmpty();
    }
  }
}
