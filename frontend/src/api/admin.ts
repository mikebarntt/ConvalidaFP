import { ApiError, request } from './client'
import type { AuditEntry, DatasetVersion, ImportSummary } from './types'

/**
 * API de administración.
 *
 * Flujo CSRF: antes de cualquier POST se pide el token a /admin/csrf y se envía en la cabecera
 * X-XSRF-TOKEN. Al iniciar sesión el servidor cambia el token y devuelve el nuevo, que sustituye
 * al anterior. Si un POST se rechaza con 403 (token caducado), se pide otro y se reintenta una vez.
 */
const BASE = '/api/v1/admin'

let csrfToken: string | null = null
let unauthorizedHandler: (() => void) | null = null

/** Qué hacer cuando la sesión ha caducado (una petición de administración recibe un 401). */
export function onUnauthorized(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

/** Solo para los tests: olvida el token guardado. */
export function resetCsrfToken(): void {
  csrfToken = null
}

async function ensureCsrfToken(refresh = false): Promise<string> {
  if (!csrfToken || refresh) {
    const response = await request<{ token: string }>(`${BASE}/csrf`)
    csrfToken = response.token
  }
  return csrfToken
}

interface AdminRequestOptions {
  /** Si un 401 debe tratarse como sesión caducada. En el login no: ahí significa credenciales malas. */
  notifyUnauthorized?: boolean
  retryCsrf?: boolean
}

async function adminRequest<T>(
  path: string,
  init: RequestInit = {},
  { notifyUnauthorized = true, retryCsrf = true }: AdminRequestOptions = {},
): Promise<T> {
  const writes = (init.method ?? 'GET').toUpperCase() !== 'GET'
  const headers: Record<string, string> = { ...(init.headers as Record<string, string> | undefined) }
  if (writes) {
    headers['X-XSRF-TOKEN'] = await ensureCsrfToken()
  }
  try {
    return await request<T>(`${BASE}${path}`, { ...init, headers })
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403 && writes && retryCsrf) {
        await ensureCsrfToken(true)
        return adminRequest<T>(path, init, { notifyUnauthorized, retryCsrf: false })
      }
      if (error.status === 401 && notifyUnauthorized) {
        unauthorizedHandler?.()
      }
    }
    throw error
  }
}

// ---------- sesión ----------

/** Inicia sesión. Las credenciales se envían como formulario, que es lo que espera el servidor. */
export async function login(username: string, password: string): Promise<string> {
  const response = await adminRequest<{ username: string; csrfToken: string }>(
    '/login',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ username, password }).toString(),
    },
    { notifyUnauthorized: false },
  )
  // El token cambia al iniciar sesión: a partir de ahora vale el nuevo.
  csrfToken = response.csrfToken
  return response.username
}

export async function logout(): Promise<void> {
  await adminRequest<void>('/logout', { method: 'POST' }, { notifyUnauthorized: false })
  csrfToken = null
}

/** El administrador con la sesión iniciada, o null si no hay sesión. */
export async function currentSession(): Promise<string | null> {
  try {
    const response = await adminRequest<{ username: string }>('/session', {}, { notifyUnauthorized: false })
    return response.username
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

// ---------- importaciones, versiones y auditoría ----------

export function listImports(): Promise<ImportSummary[]> {
  return adminRequest('/imports')
}

export function getImport(id: number): Promise<ImportSummary> {
  return adminRequest(`/imports/${id}`)
}

export function uploadDataset(file: File): Promise<ImportSummary> {
  const body = new FormData()
  body.append('file', file)
  return adminRequest('/imports', { method: 'POST', body })
}

export function publishImport(id: number): Promise<ImportSummary> {
  return adminRequest(`/imports/${id}/publish`, { method: 'POST' })
}

export function listVersions(): Promise<DatasetVersion[]> {
  return adminRequest('/versions')
}

export function restoreVersion(id: number): Promise<DatasetVersion> {
  return adminRequest(`/versions/${id}/restore`, { method: 'POST' })
}

export function listAudit(limit = 200): Promise<AuditEntry[]> {
  return adminRequest(`/audit?limit=${limit}`)
}
