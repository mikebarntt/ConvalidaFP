// El estado de las pantallas públicas vive en la URL, para poder compartirla.

import type { LocationQueryValue } from 'vue-router'

export const MAX_COMPARED_DEGREES = 4

type QueryValue = LocationQueryValue | LocationQueryValue[] | undefined

/** Un parámetro de la URL como texto; vacío si no está. */
export function queryText(value: QueryValue): string {
  const first = Array.isArray(value) ? value[0] : value
  return typeof first === 'string' ? first.trim() : ''
}

/** «a,b,,a,c» → ['a', 'b', 'c']: sin vacíos ni repetidos, y como máximo 4. */
export function parseDegreeList(value: QueryValue): string[] {
  const unique: string[] = []
  for (const id of queryText(value).split(',')) {
    const trimmed = id.trim()
    if (trimmed && !unique.includes(trimmed)) {
      unique.push(trimmed)
    }
  }
  return unique.slice(0, MAX_COMPARED_DEGREES)
}

/**
 * Adónde volver después del login. Solo se aceptan rutas internas del panel de administración:
 * así un enlace manipulado no puede llevar a otro sitio.
 */
export function safeAdminRedirect(value: QueryValue, fallback = '/admin/importaciones'): string {
  const target = queryText(value)
  return target.startsWith('/admin/') && !target.startsWith('/admin/login') && !target.includes('//')
    ? target
    : fallback
}
