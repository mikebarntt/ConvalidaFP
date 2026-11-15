// Formato de cifras, fechas y etiquetas en español, para estudiantes y no para programadores.

const NUMBER = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 })
const DATE = new Intl.DateTimeFormat('es-ES', { dateStyle: 'long', timeZone: 'UTC' })
const DATE_TIME = new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' })

/** 4.5 → «4,5»; 60 → «60». */
export function formatNumber(value: number): string {
  return NUMBER.format(value)
}

/** 4.5 → «4,5 ECTS». Un valor desconocido se dice, no se inventa. */
export function formatEcts(value: number | null | undefined): string {
  return value === null || value === undefined ? 'ECTS sin determinar' : `${formatNumber(value)} ECTS`
}

/** 21.25 → «21,25 %». */
export function formatPercentage(value: number): string {
  return `${formatNumber(value)} %`
}

/** «2025-03-14» → «14 de marzo de 2025». */
export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return 'fecha no disponible'
  }
  const date = new Date(value.length === 10 ? `${value}T00:00:00Z` : value)
  return Number.isNaN(date.getTime()) ? 'fecha no disponible' : DATE.format(date)
}

/** Fecha y hora local, para el panel de administración. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : DATE_TIME.format(date)
}

/** 1 → «1.º curso»; null → «Curso sin determinar». */
export function formatCourseYear(year: number | null): string {
  return year === null ? 'Curso sin determinar' : `${year}.º curso`
}

function label(labels: Record<string, string>, value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  return labels[value] ?? value
}

const DOCUMENT_TYPES: Record<string, string> = {
  CONVENIO: 'Convenio',
  ADENDA: 'Adenda',
  TABLA_UNIVERSIDAD: 'Tabla de la universidad',
  BOLETIN_OFICIAL: 'Boletín oficial',
  OTRO: 'Otro documento',
}

const SUBJECT_TYPES: Record<string, string> = {
  BASICA: 'Básica',
  OBLIGATORIA: 'Obligatoria',
  OPTATIVA: 'Optativa',
  PRACTICAS_EXTERNAS: 'Prácticas externas',
  TFG: 'Trabajo fin de grado',
}

const TABLE_STATUS: Record<string, string> = {
  VIGENTE: 'con tabla de reconocimiento',
  SIN_CONVENIO: 'sin convenio',
}

const VERSION_STATUS: Record<string, string> = {
  BORRADOR: 'Borrador',
  PUBLICADO: 'Publicada',
  ARCHIVADO: 'Archivada',
}

const IMPORT_OUTCOMES: Record<string, string> = {
  ACEPTADA: 'Aceptada',
  RECHAZADA: 'Rechazada',
}

const AUDIT_ACTIONS: Record<string, string> = {
  IMPORTAR: 'Importación',
  PUBLICAR: 'Publicación',
  RESTAURAR: 'Restauración',
  LOGIN_FALLIDO: 'Login fallido',
}

export const documentTypeLabel = (value: string | null | undefined) => label(DOCUMENT_TYPES, value)
export const subjectTypeLabel = (value: string | null | undefined) => label(SUBJECT_TYPES, value)
export const tableStatusLabel = (value: string | null | undefined) => (value ? label(TABLE_STATUS, value) : 'sin datos')
export const versionStatusLabel = (value: string | null | undefined) => label(VERSION_STATUS, value)
export const importOutcomeLabel = (value: string | null | undefined) => label(IMPORT_OUTCOMES, value)
export const auditActionLabel = (value: string | null | undefined) => label(AUDIT_ACTIONS, value)

/** «Grado de Prueba (Escuela de Ejemplo)»: el centro distingue el mismo grado en dos centros. */
export function degreeLabel(degree: { name: string; center: string | null }): string {
  return degree.center ? `${degree.name} (${degree.center})` : degree.name
}
