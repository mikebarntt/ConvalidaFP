import type { Cycle } from '@/api/types'

function sameName(a: string, b: string): boolean {
  return a.trim().toLocaleLowerCase('es') === b.trim().toLocaleLowerCase('es')
}

/**
 * Ayuda para elegir el ciclo. Solo cuando otro ciclo tiene el mismo nombre y distinta normativa,
 * y solo con el texto del dataset (columna help_text): el frontend no escribe ninguna ayuda propia.
 */
export function cycleHelpText(cycle: Cycle, cycles: readonly Cycle[]): string | null {
  const help = cycle.helpText?.trim()
  if (!help) {
    return null
  }
  const hasTwin = cycles.some(
    (other) =>
      other.id !== cycle.id &&
      sameName(other.name, cycle.name) &&
      (other.regulationRef ?? '') !== (cycle.regulationRef ?? ''),
  )
  return hasTwin ? help : null
}
