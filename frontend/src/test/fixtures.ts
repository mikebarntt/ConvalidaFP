// Datos ficticios para los tests de las pantallas, con la misma forma que devuelve la API.

import type { Citation, Simulation } from '@/api/types'

export const citation: Citation = {
  sourceId: 'S-EJ-01',
  documentType: 'CONVENIO',
  title: 'Convenio Ficticio de Reconocimiento',
  publicationDate: '2025-03-14',
  url: 'https://uej.example/docs/convenio-ficticio.pdf',
  page: 3,
  excerpt: 'Módulo Ficticio 07 - Desarrollo Web',
}

const base: Simulation = {
  outcome: 'RECOGNIZED',
  dataset: { versionId: 2, academicYear: '2026-27', publishedAt: '2026-11-14T21:18:53.720331Z' },
  cycle: {
    id: 'CF-EJ',
    name: 'Ciclo Ficticio de Desarrollo de Aplicaciones de Ejemplo',
    acronym: 'CFE',
    professionalFamily: 'Informática Ficticia',
    regulationRef: 'Norma Ficticia 01/2010',
    totalEcts: 120,
    helpText: null,
  },
  degree: {
    id: 'UEJ-GP',
    universityId: 'UEJ',
    universityName: 'Universidad de Ejemplo',
    name: 'Grado de Prueba en Informática Ficticia',
    degreeType: 'SIMPLE',
    center: 'Escuela de Ejemplo',
    totalEcts: 240,
    planCode: 'PLAN-EJ-2020',
    url: null,
    tableStatus: null,
  },
  notice: 'Resultado orientativo, no vinculante.',
  tableId: 'T-EJ-01',
  recognizedSubjects: [
    {
      subjectId: 'UEJ-GP-PROG1',
      name: 'Programación de Prueba I',
      ects: 6,
      courseYear: 1,
      ruleId: 'R-001',
      alternativeRuleIds: [],
      source: { ...citation, excerpt: 'Módulo Ficticio 01 - Programación I' },
    },
    {
      subjectId: 'UEJ-GP-LM',
      name: 'Lenguajes de Marcas de Ejemplo',
      ects: 4.5,
      courseYear: 2,
      ruleId: 'R-008',
      alternativeRuleIds: [],
      source: citation,
    },
  ],
  optativeCredits: [{ ruleId: 'R-003', ects: 6, source: { ...citation, page: 4, excerpt: '6 créditos optativos' } }],
  conditionalRecognitions: [
    {
      ruleId: 'R-004',
      subjectId: 'UEJ-GP-PRACT',
      subjectName: 'Prácticas Externas de Prueba',
      ects: 12,
      conditionText: 'Solo si las prácticas son de naturaleza similar',
      source: { ...citation, page: 4 },
    },
  ],
  rulesTotalEcts: 16.5,
  legalCapEcts: 60,
  tableCapEcts: null,
  appliedCapEcts: 60,
  recognizedEcts: 16.5,
  capped: false,
  degreeTotalEcts: 240,
  pendingEcts: 223.5,
  recognizedPercentage: 6.88,
  subjectsComplete: true,
  remainingByYear: [
    {
      courseYear: 1,
      subjects: [{ id: 'UEJ-GP-MAT1', name: 'Matemática Ficticia I', ects: 6, subjectType: 'BASICA' }],
    },
    {
      courseYear: null,
      subjects: [{ id: 'UEJ-GP-X', name: 'Asignatura de Prueba sin curso', ects: null, subjectType: 'OPTATIVA' }],
    },
  ],
  noAgreement: null,
}

export function recognized(overrides: Partial<Simulation> = {}): Simulation {
  return { ...base, ...overrides }
}

/** Las tablas suman 66 ECTS y el tope del 25 % es 60. */
export function capped(overrides: Partial<Simulation> = {}): Simulation {
  return recognized({
    rulesTotalEcts: 66,
    recognizedEcts: 60,
    capped: true,
    pendingEcts: 180,
    recognizedPercentage: 25,
    ...overrides,
  })
}

function withoutRecognition(outcome: 'NO_DATA' | 'NO_AGREEMENT'): Simulation {
  return {
    ...base,
    outcome,
    tableId: null,
    recognizedSubjects: [],
    optativeCredits: [],
    conditionalRecognitions: [],
    rulesTotalEcts: null,
    legalCapEcts: null,
    tableCapEcts: null,
    appliedCapEcts: null,
    recognizedEcts: null,
    capped: null,
    pendingEcts: null,
    recognizedPercentage: null,
    remainingByYear: [],
  }
}

export function noData(): Simulation {
  return withoutRecognition('NO_DATA')
}

export function noAgreement(): Simulation {
  return {
    ...withoutRecognition('NO_AGREEMENT'),
    tableId: 'T-FI-01',
    noAgreement: {
      tableId: 'T-FI-01',
      source: {
        sourceId: 'S-FI-01',
        documentType: 'OTRO',
        title: 'Comunicación de Ejemplo: inexistencia de tabla de reconocimiento',
        publicationDate: null,
        url: null,
      },
      notes: 'Verificado: no existe tabla firmada',
    },
  }
}
