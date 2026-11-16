// Tipos de las respuestas de la API. Un valor null significa «desconocido».

export interface DatasetInfo {
  versionId: number
  academicYear: string
  publishedAt: string
}

export interface DatasetResponse extends DatasetInfo {
  availableYears: string[]
}

export interface Cycle {
  id: string
  name: string
  acronym: string | null
  professionalFamily: string | null
  regulationRef: string | null
  totalEcts: number | null
  /** Cómo saber si este es tu ciclo cuando otro tiene el mismo nombre y distinta normativa. */
  helpText: string | null
}

export interface University {
  id: string
  name: string
  shortName: string | null
  ownership: string
  region: string | null
  websiteUrl: string | null
}

export type TableStatus = 'VIGENTE' | 'SIN_CONVENIO'

export interface Degree {
  id: string
  universityId: string
  universityName: string
  name: string
  degreeType: string
  center: string | null
  totalEcts: number | null
  planCode: string | null
  url: string | null
  tableStatus: TableStatus | null
}

export interface SourceDocument {
  id: string
  documentType: string
  title: string
  issuer: string | null
  publicationDate: string | null
  url: string | null
  fileSha256: string | null
  retrievedAt: string | null
}

/** Documento, página y fragmento literal de los que sale un reconocimiento. */
export interface Citation {
  sourceId: string | null
  documentType: string | null
  title: string | null
  publicationDate: string | null
  url: string | null
  page: number
  excerpt: string | null
}

export interface RecognizedSubject {
  subjectId: string
  name: string
  ects: number
  courseYear: number | null
  ruleId: string
  alternativeRuleIds: string[]
  source: Citation
}

export interface OptativeCredits {
  ruleId: string
  ects: number
  source: Citation
}

export interface ConditionalRecognition {
  ruleId: string
  subjectId: string | null
  subjectName: string | null
  ects: number | null
  conditionText: string | null
  source: Citation
}

export interface RemainingSubject {
  id: string
  name: string
  ects: number | null
  subjectType: string
}

export interface RemainingYear {
  courseYear: number | null
  subjects: RemainingSubject[]
}

export interface NoAgreement {
  tableId: string
  source: Omit<Citation, 'page' | 'excerpt'> | null
  notes: string | null
}

export type Outcome = 'RECOGNIZED' | 'NO_AGREEMENT' | 'NO_DATA'

export interface Simulation {
  outcome: Outcome
  dataset: DatasetInfo
  cycle: Cycle
  degree: Degree
  notice: string
  tableId: string | null
  recognizedSubjects: RecognizedSubject[]
  optativeCredits: OptativeCredits[]
  conditionalRecognitions: ConditionalRecognition[]
  rulesTotalEcts: number | null
  legalCapEcts: number | null
  tableCapEcts: number | null
  appliedCapEcts: number | null
  recognizedEcts: number | null
  capped: boolean | null
  degreeTotalEcts: number | null
  pendingEcts: number | null
  recognizedPercentage: number | null
  /** false: el dataset solo trae las asignaturas de las tablas de reconocimiento y remainingByYear va vacío. */
  subjectsComplete: boolean
  remainingByYear: RemainingYear[]
  noAgreement: NoAgreement | null
}

export interface Comparison {
  dataset: DatasetInfo
  cycle: Cycle
  results: Simulation[]
}

export interface ExploreEntry {
  degree: Degree
  recognizedEcts: number
  recognizedPercentage: number
  capped: boolean
  rulesTotalEcts: number
  recognizedSubjectCount: number
}

export interface Explore {
  dataset: DatasetInfo
  cycle: Cycle
  notice: string
  results: ExploreEntry[]
}

// ---------- administración ----------

export interface ValidationIssue {
  severity: 'ERROR' | 'WARNING'
  code: string
  file: string
  row: number | null
  column: string | null
  message: string
}

export interface FieldChange {
  column: string
  before: string
  after: string
}

export interface RowChange {
  id: string
  fields: FieldChange[]
}

export interface FileDiff {
  file: string
  added: string[]
  removed: string[]
  changed: RowChange[]
}

export interface DatasetDiff {
  baselineVersionId: number | null
  baselineAcademicYear: string | null
  files: FileDiff[]
}

export interface ImportReport {
  errors: ValidationIssue[]
  warnings: ValidationIssue[]
  diff: DatasetDiff | null
}

export type VersionStatus = 'BORRADOR' | 'PUBLICADO' | 'ARCHIVADO'

export interface ImportSummary {
  id: number
  createdAt: string
  createdBy: string
  fileName: string | null
  academicYear: string | null
  outcome: 'ACEPTADA' | 'RECHAZADA'
  datasetVersionId: number | null
  versionStatus: VersionStatus | null
  errorCount: number
  warningCount: number
  report: ImportReport | null
}

export interface DatasetVersion {
  id: number
  academicYear: string
  status: VersionStatus
  manifestDate: string | null
  reviewer: string | null
  createdAt: string
  publishedAt: string | null
  archivedAt: string | null
}

export interface AuditEntry {
  id: number
  occurredAt: string
  actor: string
  action: 'IMPORTAR' | 'PUBLICAR' | 'RESTAURAR' | 'LOGIN_FALLIDO'
  datasetVersionId: number | null
  academicYear: string | null
  importId: number | null
  detail: string | null
  ip: string | null
}
