import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  currentSession,
  listAudit,
  login,
  logout,
  onUnauthorized,
  publishImport,
  resetCsrfToken,
  uploadDataset,
} from './admin'
import { ApiError } from './client'

interface Call {
  url: string
  method: string
  headers: Record<string, string>
  body: unknown
}

type Reply = { status: number; body?: unknown }

/** Un servidor de mentira: responde según la ruta y guarda las peticiones que recibe. */
function fakeServer(handler: (call: Call) => Reply): Call[] {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      const call: Call = {
        url,
        method: (init.method ?? 'GET').toUpperCase(),
        headers: (init.headers ?? {}) as Record<string, string>,
        body: init.body,
      }
      calls.push(call)
      const reply = handler(call)
      const text = reply.body === undefined ? null : JSON.stringify(reply.body)
      return new Response(reply.status === 204 ? null : text, { status: reply.status })
    }),
  )
  return calls
}

const CSRF = '/api/v1/admin/csrf'

beforeEach(() => {
  resetCsrfToken()
  onUnauthorized(null)
})
afterEach(() => vi.unstubAllGlobals())

describe('flujo CSRF', () => {
  it('pide el token antes del login, envía el formulario y usa el token nuevo después', async () => {
    const calls = fakeServer((call) => {
      if (call.url === CSRF) return { status: 200, body: { token: 'token-anonimo' } }
      if (call.url.endsWith('/login')) return { status: 200, body: { username: 'admin', csrfToken: 'token-de-sesion' } }
      return { status: 200, body: { id: 7 } }
    })

    await expect(login('admin', 'clave secreta & rara')).resolves.toBe('admin')

    expect(calls.map((c) => `${c.method} ${c.url}`)).toEqual([`GET ${CSRF}`, 'POST /api/v1/admin/login'])
    const loginCall = calls[1]
    expect(loginCall.headers['X-XSRF-TOKEN']).toBe('token-anonimo')
    // El login se envía como formulario, no como JSON.
    expect(loginCall.headers['Content-Type']).toBe('application/x-www-form-urlencoded')
    expect(loginCall.body).toBe('username=admin&password=clave+secreta+%26+rara')

    // El siguiente POST usa el token que devolvió el login, sin volver a pedirlo.
    await publishImport(7)
    expect(calls).toHaveLength(3)
    expect(calls[2].headers['X-XSRF-TOKEN']).toBe('token-de-sesion')
  })

  it('las peticiones GET no llevan token ni lo piden', async () => {
    const calls = fakeServer(() => ({ status: 200, body: [] }))
    await listAudit(50)
    expect(calls).toHaveLength(1)
    expect(calls[0].url).toBe('/api/v1/admin/audit?limit=50')
    expect(calls[0].headers['X-XSRF-TOKEN']).toBeUndefined()
  })

  it('reutiliza el token entre peticiones POST', async () => {
    const calls = fakeServer((call) =>
      call.url === CSRF ? { status: 200, body: { token: 'uno' } } : { status: 200, body: {} },
    )
    await publishImport(1)
    await publishImport(2)
    expect(calls.filter((c) => c.url === CSRF)).toHaveLength(1)
  })

  it('si un POST da 403 por un token caducado, pide otro y reintenta una sola vez', async () => {
    let tokens = 0
    const calls = fakeServer((call) => {
      if (call.url === CSRF) return { status: 200, body: { token: `token-${++tokens}` } }
      return call.headers['X-XSRF-TOKEN'] === 'token-2' ? { status: 200, body: { id: 3 } } : { status: 403, body: {} }
    })

    await expect(publishImport(3)).resolves.toEqual({ id: 3 })
    expect(calls.map((c) => c.headers['X-XSRF-TOKEN'] ?? c.url)).toEqual([CSRF, 'token-1', CSRF, 'token-2'])
  })

  it('un 403 que se repite no se reintenta sin fin', async () => {
    const calls = fakeServer((call) =>
      call.url === CSRF
        ? { status: 200, body: { token: 't' } }
        : { status: 403, body: { detail: 'No tienes permiso' } },
    )
    await expect(publishImport(3)).rejects.toMatchObject({ status: 403 })
    expect(calls.filter((c) => c.method === 'POST')).toHaveLength(2)
  })

  it('sube el ZIP como formulario multipart con el token', async () => {
    const calls = fakeServer((call) =>
      call.url === CSRF ? { status: 200, body: { token: 't' } } : { status: 201, body: { id: 9 } },
    )
    await uploadDataset(new File(['zip'], 'dataset.zip', { type: 'application/zip' }))
    const upload = calls[1]
    expect(upload.body).toBeInstanceOf(FormData)
    expect((upload.body as FormData).get('file')).toBeInstanceOf(File)
    expect(upload.headers['X-XSRF-TOKEN']).toBe('t')
    // El navegador pone el Content-Type del multipart con su separador: no se fija a mano.
    expect(upload.headers['Content-Type']).toBeUndefined()
  })

  it('al cerrar sesión olvida el token', async () => {
    const calls = fakeServer((call) => (call.url === CSRF ? { status: 200, body: { token: 't' } } : { status: 204 }))
    await logout()
    await logout()
    expect(calls.filter((c) => c.url === CSRF)).toHaveLength(2)
  })
})

describe('sesión caducada', () => {
  it('avisa cuando una petición de administración recibe un 401', async () => {
    const expired = vi.fn()
    onUnauthorized(expired)
    fakeServer(() => ({ status: 401, body: { detail: 'Inicia sesión como administrador.' } }))

    await expect(listAudit()).rejects.toBeInstanceOf(ApiError)
    expect(expired).toHaveBeenCalledTimes(1)
  })

  it('un 401 en el login son credenciales incorrectas, no una sesión caducada', async () => {
    const expired = vi.fn()
    onUnauthorized(expired)
    fakeServer((call) =>
      call.url === CSRF
        ? { status: 200, body: { token: 't' } }
        : { status: 401, body: { detail: 'Usuario o contraseña incorrectos.' } },
    )

    await expect(login('admin', 'mala')).rejects.toMatchObject({ detail: 'Usuario o contraseña incorrectos.' })
    expect(expired).not.toHaveBeenCalled()
  })

  it('comprobar la sesión devuelve el usuario, o null si no hay sesión, sin avisar', async () => {
    const expired = vi.fn()
    onUnauthorized(expired)

    fakeServer(() => ({ status: 200, body: { username: 'admin' } }))
    await expect(currentSession()).resolves.toBe('admin')

    fakeServer(() => ({ status: 401, body: {} }))
    await expect(currentSession()).resolves.toBeNull()
    expect(expired).not.toHaveBeenCalled()

    fakeServer(() => ({ status: 500, body: {} }))
    await expect(currentSession()).rejects.toMatchObject({ status: 500 })
  })
})
