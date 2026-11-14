package es.convalidafp.ingestion;

import java.util.List;

/**
 * Informe de validación de un dataset, en dos niveles: los errores bloquean la carga entera; los
 * avisos no la bloquean y se revisan a mano antes de publicar.
 */
public record ValidationReport(List<Issue> errors, List<Issue> warnings) {

  public ValidationReport {
    errors = List.copyOf(errors);
    warnings = List.copyOf(warnings);
  }

  /** El dataset se puede cargar: no tiene ningún error. */
  public boolean isValid() {
    return errors.isEmpty();
  }

  /** Nivel de una incidencia. */
  public enum Severity {
    ERROR,
    WARNING
  }

  /**
   * Una incidencia: fichero, fila, columna y motivo.
   *
   * @param code código de la validación en CONTRACT.md (V1–V12, F1–F11, A1–A3)
   * @param row número de fila en el fichero (la cabecera es la 1); null si afecta al fichero entero
   * @param column columna afectada; null si afecta a la fila o al fichero entero
   */
  public record Issue(Severity severity, String code, String file, Long row, String column, String message) {

    @Override
    public String toString() {
      StringBuilder text = new StringBuilder("[").append(code).append("] ").append(file);
      if (row != null) {
        text.append(": fila ").append(row);
      }
      if (column != null) {
        text.append(": columna ").append(column);
      }
      return text.append(": ").append(message).toString();
    }
  }
}
