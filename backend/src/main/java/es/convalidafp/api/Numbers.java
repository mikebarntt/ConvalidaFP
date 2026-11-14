package es.convalidafp.api;

import java.math.BigDecimal;

/** Presentación de cifras de ECTS. */
final class Numbers {

  private Numbers() {}

  /**
   * Quita los ceros de relleno (6.00 → 6, 4.50 → 4.5) sin cambiar el valor ni pasar a notación
   * científica. null (desconocido) se queda en null.
   */
  static BigDecimal plain(BigDecimal value) {
    if (value == null) {
      return null;
    }
    BigDecimal stripped = value.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }
}
