import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import HomeView from './HomeView.vue'

describe('HomeView', () => {
  it('muestra el título y el aviso de que está en construcción', () => {
    const wrapper = mount(HomeView)
    expect(wrapper.get('h1').text()).toContain('Simulador de convalidaciones')
    expect(wrapper.get('[role="status"]').text()).toContain('En construcción')
  })
})
