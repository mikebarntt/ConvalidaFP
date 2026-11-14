package es.convalidafp.ingestion;

/**
 * Resultado de una importación.
 *
 * <ul>
 *   <li>Rechazada: el informe tiene errores, no se ha guardado nada y no hay versión ni diferencias.
 *   <li>Aceptada: el dataset queda guardado como versión en borrador, con sus avisos (si los hay) y
 *       las diferencias con lo publicado, a la espera de que alguien lo publique.
 * </ul>
 *
 * @param academicYear curso del manifiesto; null si el manifiesto no se pudo leer
 * @param datasetVersionId versión en borrador creada; null si la importación se rechazó
 * @param diff diferencias con lo publicado; null si la importación se rechazó
 */
public record ImportResult(String academicYear, ValidationReport report, Long datasetVersionId, DatasetDiff diff) {

  public boolean accepted() {
    return datasetVersionId != null;
  }
}
