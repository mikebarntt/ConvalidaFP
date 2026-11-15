import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { currentSession } from '@/api/admin'
import { useAdminStore } from '@/stores/admin'

vi.mock('@/api/admin', () => ({
  currentSession: vi.fn(),
  login: vi.fn(async (username: string) => username),
  logout: vi.fn(async () => undefined),
}))

/** Las mismas rutas y reglas de navegación de la aplicación, con el historial en memoria. */
async function createTestRouter(): Promise<Router> {
  const { routes, installGuards } = await import('./index')
  const router = createRouter({ history: createMemoryHistory(), routes })
  installGuards(router)
  return router
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(currentSession).mockReset()
})

describe('acceso a las pantallas de administración', () => {
  it('sin sesión, lleva al login recordando la pantalla que se quería ver', async () => {
    vi.mocked(currentSession).mockResolvedValue(null)
    const router = await createTestRouter()

    await router.push('/admin/importaciones/7')

    expect(router.currentRoute.value.name).toBe('admin-login')
    expect(router.currentRoute.value.query.redirect).toBe('/admin/importaciones/7')
  })

  it('con sesión, deja pasar y solo pregunta al servidor una vez', async () => {
    vi.mocked(currentSession).mockResolvedValue('admin')
    const router = await createTestRouter()

    await router.push('/admin/auditoria')
    await router.push('/admin/importaciones')

    expect(router.currentRoute.value.name).toBe('admin-imports')
    expect(currentSession).toHaveBeenCalledTimes(1)
    expect(useAdminStore().username).toBe('admin')
  })

  it('las pantallas públicas y el login no preguntan por la sesión', async () => {
    const router = await createTestRouter()

    for (const path of [
      '/',
      '/resultado?ciclo=CF-EJ&grado=UEJ-GP',
      '/comparar',
      '/explorar',
      '/fuentes',
      '/admin/login',
    ]) {
      await router.push(path)
      expect(router.currentRoute.value.fullPath).toBe(path)
    }
    expect(currentSession).not.toHaveBeenCalled()
  })

  it('/admin lleva a importaciones y una ruta desconocida a la página de error', async () => {
    vi.mocked(currentSession).mockResolvedValue('admin')
    const router = await createTestRouter()

    await router.push('/admin')
    expect(router.currentRoute.value.name).toBe('admin-imports')
    await router.push('/no-existe')
    expect(router.currentRoute.value.name).toBe('not-found')
  })

  it('al cambiar de pantalla se actualiza el título de la pestaña', async () => {
    const router = await createTestRouter()
    await router.push('/fuentes')
    expect(document.title).toBe('Fuentes y metodología · ConvalidaFP')
  })

  it('todas las pantallas tienen título', async () => {
    const { routes } = await import('./index')
    for (const route of routes.filter((r) => !r.redirect)) {
      expect(route.meta?.title, String(route.name)).toBeTruthy()
    }
  })
})

describe('sesión del administrador', () => {
  it('al iniciar y cerrar sesión refleja el estado', async () => {
    const admin = useAdminStore()
    expect(admin.isLoggedIn).toBe(false)

    await admin.login('admin', 'clave')
    expect(admin.isLoggedIn).toBe(true)
    expect(admin.username).toBe('admin')

    await admin.logout()
    expect(admin.isLoggedIn).toBe(false)
    expect(admin.checked).toBe(true)
  })
})
