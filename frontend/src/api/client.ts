/**
 * Error de la API. La API responde en formato Problem Details (RFC 9457) con mensajes en español
 * pensados para mostrarse tal cual; si la respuesta no trae uno, se usa un mensaje propio.
 */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail: string

  constructor(status: number, title: string, detail: string) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.detail = detail
  }
}

const FALLBACK_MESSAGES: Record<number, string> = {
  400: 'La petición no es válida.',
  401: 'Tienes que iniciar sesión.',
  403: 'No tienes permiso para hacer esto.',
  404: 'No hemos encontrado lo que buscas.',
  409: 'La operación no se puede hacer en el estado actual.',
  413: 'El fichero es demasiado grande.',
  429: 'Has hecho demasiadas peticiones. Espera un momento y vuelve a intentarlo.',
}

function fallbackMessage(status: number): string {
  if (FALLBACK_MESSAGES[status]) {
    return FALLBACK_MESSAGES[status]
  }
  return status >= 500
    ? 'El servicio no está disponible ahora mismo. Inténtalo de nuevo en unos minutos.'
    : 'No se ha podido completar la operación.'
}

function toApiError(status: number, body: unknown): ApiError {
  const problem = (body ?? {}) as { title?: unknown; detail?: unknown }
  const detail = typeof problem.detail === 'string' && problem.detail ? problem.detail : fallbackMessage(status)
  const title = typeof problem.title === 'string' && problem.title ? problem.title : 'Error'
  return new ApiError(status, title, detail)
}

/** Hace una petición a la API y devuelve el cuerpo JSON. Lanza {@link ApiError} si falla. */
export async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, {
      credentials: 'same-origin',
      // Siempre se pregunta al servidor si la respuesta guardada sigue valiendo (ETag). Si no ha
      // cambiado responde 304 sin cuerpo; si se ha publicado una versión nueva, se ve al instante.
      cache: 'no-cache',
      ...init,
      headers: { Accept: 'application/json', ...(init.headers as Record<string, string> | undefined) },
    })
  } catch {
    throw new ApiError(
      0,
      'Sin conexión',
      'No se ha podido conectar con el servidor. Comprueba tu conexión e inténtalo de nuevo.',
    )
  }
  const text = response.status === 204 ? '' : await response.text()
  let body: unknown = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = null
    }
  }
  if (!response.ok) {
    throw toApiError(response.status, body)
  }
  // Una respuesta sin cuerpo (por ejemplo, un 204) no devuelve nada.
  return (text ? body : undefined) as T
}

/** Construye una URL con parámetros, omitiendo los vacíos. */
export function withQuery(path: string, params: Record<string, string | undefined | null>): string {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value) {
      query.set(key, value)
    }
  }
  const text = query.toString()
  return text ? `${path}?${text}` : path
}

/** El mensaje que se enseña al usuario para cualquier error. */
export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.detail : 'Ha ocurrido un error inesperado.'
}
