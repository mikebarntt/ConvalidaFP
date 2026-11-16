import { describe, expect, it } from 'vitest'
import type { Cycle } from '@/api/types'
import { cycleHelpText } from './cycles'

function cycle(id: string, name: string, regulationRef: string | null, helpText: string | null): Cycle {
  return { id, name, acronym: null, professionalFamily: null, regulationRef, totalEcts: 120, helpText }
}

const old = cycle('CF-EJ', 'Ciclo Ficticio', 'Norma Ficticia 01/2010', 'Si tu título cita la Norma 01/2010.')
const updated = cycle('CF-EJ-2024', 'Ciclo Ficticio', 'Norma Ficticia 02/2024', 'Si tu título cita la Norma 02/2024.')
const other = cycle('CF-PR', 'Ciclo de Prueba', 'Norma Ficticia 03/2011', 'Texto que no hace falta enseñar.')

describe('ayuda para elegir el ciclo', () => {
  it('se muestra cuando otro ciclo tiene el mismo nombre y distinta normativa', () => {
    const all = [old, updated, other]
    expect(cycleHelpText(old, all)).toBe('Si tu título cita la Norma 01/2010.')
    expect(cycleHelpText(updated, all)).toBe('Si tu título cita la Norma 02/2024.')
  })

  it('no se muestra si el nombre del ciclo no se repite', () => {
    expect(cycleHelpText(other, [old, updated, other])).toBeNull()
    expect(cycleHelpText(old, [old, other])).toBeNull()
  })

  it('compara los nombres sin distinguir mayúsculas ni espacios de los extremos', () => {
    const twin = { ...updated, name: '  CICLO FICTICIO ' }
    expect(cycleHelpText(old, [old, twin])).toBe('Si tu título cita la Norma 01/2010.')
  })

  it('no se muestra si los dos tienen la misma normativa', () => {
    const duplicate = { ...updated, regulationRef: old.regulationRef }
    expect(cycleHelpText(old, [old, duplicate])).toBeNull()
  })

  it('sin texto en el dataset no hay ayuda: el frontend no la inventa', () => {
    expect(cycleHelpText({ ...old, helpText: null }, [old, updated])).toBeNull()
    expect(cycleHelpText({ ...old, helpText: '   ' }, [old, updated])).toBeNull()
  })
})
