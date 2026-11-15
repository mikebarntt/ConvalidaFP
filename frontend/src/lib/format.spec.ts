import { describe, expect, it } from 'vitest'
import {
  auditActionLabel,
  degreeLabel,
  documentTypeLabel,
  formatCourseYear,
  formatDate,
  formatDateTime,
  formatEcts,
  formatNumber,
  formatPercentage,
  importOutcomeLabel,
  subjectTypeLabel,
  tableStatusLabel,
  versionStatusLabel,
} from './format'

describe('formato de cifras', () => {
  it('usa coma decimal y no añade ceros de relleno', () => {
    expect(formatNumber(4.5)).toBe('4,5')
    expect(formatNumber(60)).toBe('60')
    expect(formatNumber(22.5)).toBe('22,5')
    expect(formatNumber(21.25)).toBe('21,25')
  })

  it('escribe los ECTS con su unidad', () => {
    expect(formatEcts(51)).toBe('51 ECTS')
    expect(formatEcts(4.5)).toBe('4,5 ECTS')
  })

  it('no inventa un valor desconocido: lo dice', () => {
    expect(formatEcts(null)).toBe('ECTS sin determinar')
    expect(formatEcts(undefined)).toBe('ECTS sin determinar')
  })

  it('escribe los porcentajes con su signo', () => {
    expect(formatPercentage(21.25)).toBe('21,25 %')
    expect(formatPercentage(25)).toBe('25 %')
  })
})

describe('formato de fechas', () => {
  it('escribe las fechas en español', () => {
    expect(formatDate('2025-03-14')).toBe('14 de marzo de 2025')
    expect(formatDate('2026-11-14T21:18:53.720331Z')).toBe('14 de noviembre de 2026')
  })

  it('dice que no hay fecha cuando falta o no es válida', () => {
    expect(formatDate(null)).toBe('fecha no disponible')
    expect(formatDate('')).toBe('fecha no disponible')
    expect(formatDate('no-es-una-fecha')).toBe('fecha no disponible')
  })

  it('formatea fecha y hora para el panel, con raya si falta', () => {
    expect(formatDateTime('2026-11-14T21:18:53Z')).toMatch(/14 nov 2026/)
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime('nada')).toBe('—')
  })

  it('nombra el curso de una asignatura', () => {
    expect(formatCourseYear(1)).toBe('1.º curso')
    expect(formatCourseYear(null)).toBe('Curso sin determinar')
  })
})

describe('etiquetas', () => {
  it('traduce los valores de la API a español claro', () => {
    expect(documentTypeLabel('BOLETIN_OFICIAL')).toBe('Boletín oficial')
    expect(subjectTypeLabel('PRACTICAS_EXTERNAS')).toBe('Prácticas externas')
    expect(versionStatusLabel('ARCHIVADO')).toBe('Archivada')
    expect(importOutcomeLabel('RECHAZADA')).toBe('Rechazada')
    expect(auditActionLabel('LOGIN_FALLIDO')).toBe('Login fallido')
  })

  it('distingue tabla vigente, sin convenio y sin datos', () => {
    expect(tableStatusLabel('VIGENTE')).toBe('con tabla de reconocimiento')
    expect(tableStatusLabel('SIN_CONVENIO')).toBe('sin convenio')
    expect(tableStatusLabel(null)).toBe('sin datos')
  })

  it('deja pasar un valor que no conoce y devuelve vacío si no hay valor', () => {
    expect(documentTypeLabel('ALGO_NUEVO')).toBe('ALGO_NUEVO')
    expect(documentTypeLabel(null)).toBe('')
  })

  it('añade el centro al nombre del grado, que distingue el mismo grado en dos centros', () => {
    expect(degreeLabel({ name: 'Grado de Prueba', center: 'Escuela de Ejemplo, Campus Sur' })).toBe(
      'Grado de Prueba (Escuela de Ejemplo, Campus Sur)',
    )
    expect(degreeLabel({ name: 'Grado de Prueba', center: null })).toBe('Grado de Prueba')
  })
})
