import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, errorMessage, request, withQuery } from './client'

function respondWith(status: number, body: string, contentType = 'application/json'): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(status === 204 ? null : body, { status, headers: { 'Content-Type': contentType } })),
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('cliente de la API', () => {
  it('devuelve el cuerpo JSON de una respuesta correcta', async () => {
    respondWith(200, '[{"id":"CF-EJ"}]')
    await expect(request('/api/v1/cycles')).resolves.toEqual([{ id: 'CF-EJ' }])
  })

  it('una respuesta 204 no tiene cuerpo', async () => {
    respondWith(204, '')
    await expect(request('/api/v1/admin/logout', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('convierte un Problem Details en un error con su mensaje en español', async () => {
    respondWith(
      404,
      '{"title":"No encontrado","status":404,"detail":"No existe el ciclo «CF-NO» en los datos del curso 2026-27."}',
      'application/problem+json',
    )
    const error = await request('/api/v1/simulations?cycle=CF-NO&degree=X').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(404)
    expect((error as ApiError).title).toBe('No encontrado')
    expect(errorMessage(error)).toBe('No existe el ciclo «CF-NO» en los datos del curso 2026-27.')
  })

  it('muestra en español el 413 de Nginx cuando el fichero es demasiado grande', async () => {
    respondWith(
      413,
      '{"type":"about:blank","title":"Fichero demasiado grande","status":413,"detail":"El fichero supera el tamaño máximo de 25 MB."}',
      'application/problem+json',
    )
    const error = await request('/api/v1/admin/imports', { method: 'POST' }).catch((e: unknown) => e)
    expect(errorMessage(error)).toBe('El fichero supera el tamaño máximo de 25 MB.')
  })

  it('si el error no trae un mensaje, usa uno propio en español', async () => {
    respondWith(413, '<html><body>413 Request Entity Too Large</body></html>', 'text/html')
    expect(errorMessage(await request('/x').catch((e: unknown) => e))).toBe('El fichero es demasiado grande.')

    respondWith(502, '', 'text/html')
    expect(errorMessage(await request('/x').catch((e: unknown) => e))).toContain('no está disponible')

    respondWith(418, '{}')
    expect(errorMessage(await request('/x').catch((e: unknown) => e))).toBe('No se ha podido completar la operación.')

    respondWith(429, '')
    expect(errorMessage(await request('/x').catch((e: unknown) => e))).toContain('demasiadas peticiones')
  })

  it('explica que no hay conexión cuando la petición ni siquiera sale', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch')
      }),
    )
    const error = await request('/api/v1/cycles').catch((e: unknown) => e)
    expect((error as ApiError).status).toBe(0)
    expect(errorMessage(error)).toContain('No se ha podido conectar')
  })

  it('un error que no viene de la API tiene un mensaje genérico', () => {
    expect(errorMessage(new Error('interno'))).toBe('Ha ocurrido un error inesperado.')
  })

  it('pide JSON y envía las cookies solo a su propio origen', async () => {
    respondWith(200, '{}')
    await request('/api/v1/dataset')
    const init = vi.mocked(fetch).mock.calls[0][1] as RequestInit
    expect(init.credentials).toBe('same-origin')
    // Revalida siempre con el servidor: una versión recién publicada se ve sin esperar a la caché.
    expect(init.cache).toBe('no-cache')
    expect((init.headers as Record<string, string>).Accept).toBe('application/json')
  })
})

describe('parámetros de la URL', () => {
  it('añade solo los parámetros con valor', () => {
    expect(withQuery('/api/v1/degrees', { university: 'UEJ', cycle: undefined, year: '' })).toBe(
      '/api/v1/degrees?university=UEJ',
    )
    expect(withQuery('/api/v1/cycles', {})).toBe('/api/v1/cycles')
  })

  it('codifica los valores', () => {
    expect(withQuery('/api/v1/comparisons', { cycle: 'CF-EJ', degrees: 'UEJ-GP,UEJ-DG' })).toBe(
      '/api/v1/comparisons?cycle=CF-EJ&degrees=UEJ-GP%2CUEJ-DG',
    )
  })
})
