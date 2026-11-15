import { describe, expect, it } from 'vitest'
import { parseDegreeList, queryText, safeAdminRedirect } from './urlState'

describe('estado en la URL', () => {
  it('lee un parámetro como texto', () => {
    expect(queryText('CF-EJ')).toBe('CF-EJ')
    expect(queryText('  CF-EJ ')).toBe('CF-EJ')
    expect(queryText(['CF-EJ', 'CF-PR'])).toBe('CF-EJ')
    expect(queryText(undefined)).toBe('')
    expect(queryText(null)).toBe('')
  })

  it('lee la lista de grados sin vacíos ni repetidos', () => {
    expect(parseDegreeList('UEJ-GP,UEJ-DG')).toEqual(['UEJ-GP', 'UEJ-DG'])
    expect(parseDegreeList('UEJ-GP, UEJ-GP ,,UEJ-DG')).toEqual(['UEJ-GP', 'UEJ-DG'])
    expect(parseDegreeList(undefined)).toEqual([])
    expect(parseDegreeList('')).toEqual([])
  })

  it('compara como máximo 4 grados', () => {
    expect(parseDegreeList('a,b,c,d,e,f')).toEqual(['a', 'b', 'c', 'd'])
  })
})

describe('vuelta tras el login', () => {
  it('vuelve a la pantalla de administración en la que se estaba', () => {
    expect(safeAdminRedirect('/admin/auditoria')).toBe('/admin/auditoria')
    expect(safeAdminRedirect('/admin/importaciones/7')).toBe('/admin/importaciones/7')
  })

  it('sin destino, va a importaciones', () => {
    expect(safeAdminRedirect(undefined)).toBe('/admin/importaciones')
    expect(safeAdminRedirect('')).toBe('/admin/importaciones')
  })

  it('no acepta destinos fuera del panel ni enlaces a otros sitios', () => {
    expect(safeAdminRedirect('https://malo.example/admin/')).toBe('/admin/importaciones')
    expect(safeAdminRedirect('//malo.example/admin/')).toBe('/admin/importaciones')
    expect(safeAdminRedirect('/admin//malo.example')).toBe('/admin/importaciones')
    expect(safeAdminRedirect('/resultado?ciclo=CF-EJ')).toBe('/admin/importaciones')
    expect(safeAdminRedirect('/admin/login?redirect=/admin/login')).toBe('/admin/importaciones')
  })
})
