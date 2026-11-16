import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import App from './App.vue'

const stub = { template: '<p>pantalla</p>' }

async function show(): Promise<{ wrapper: VueWrapper; router: Router }> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['home', 'compare', 'explore', 'sources', 'admin-imports', 'admin-audit', 'admin-login'].map((name) => ({
      path: name === 'home' ? '/' : `/${name}`,
      name,
      component: stub,
    })),
  })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router, createPinia()] }, attachTo: document.body })
  return { wrapper, router }
}

describe('menú principal en el móvil', () => {
  it('se pliega en un botón «Menú» que abre y cierra el desplegable', async () => {
    const { wrapper } = await show()
    const button = wrapper.get('[data-testid="menu-movil"]')
    const menu = wrapper.get('#menu-principal')

    expect(button.text()).toBe('Menú')
    expect(button.attributes('aria-controls')).toBe('menu-principal')
    expect(button.attributes('aria-expanded')).toBe('false')
    // Plegado en el móvil y en línea a partir de la anchura «sm».
    expect(menu.classes()).toContain('hidden')
    expect(menu.classes()).toContain('sm:flex')
    expect(menu.findAll('a').map((a) => a.text())).toEqual(['Inicio', 'Comparar', 'Explorar', 'Fuentes'])

    await button.trigger('click')
    expect(button.attributes('aria-expanded')).toBe('true')
    expect(menu.classes()).toContain('flex')
    expect(menu.classes()).not.toContain('hidden')

    await button.trigger('click')
    expect(button.attributes('aria-expanded')).toBe('false')
    wrapper.unmount()
  })

  it('se cierra al cambiar de pantalla', async () => {
    const { wrapper, router } = await show()
    await wrapper.get('[data-testid="menu-movil"]').trigger('click')
    await router.push('/explore')
    await flushPromises()
    expect(wrapper.get('[data-testid="menu-movil"]').attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('#menu-principal').classes()).toContain('hidden')
    wrapper.unmount()
  })

  it('se cierra con Escape y devuelve el foco al botón', async () => {
    const { wrapper } = await show()
    const button = wrapper.get('[data-testid="menu-movil"]')
    await button.trigger('click')
    await wrapper.get('#menu-principal a').trigger('keydown', { key: 'Escape' })
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(button.element)
    wrapper.unmount()
  })
})
