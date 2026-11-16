import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { simulate } from '@/api/public'
import type { Simulation } from '@/api/types'
import { capped, noAgreement, noData, recognized } from '@/test/fixtures'
import ResultView from './ResultView.vue'

vi.mock('@/api/public', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/public')>()),
  simulate: vi.fn(),
}))

async function show(result: Simulation | Error, url = '/resultado?ciclo=CF-EJ&grado=UEJ-GP'): Promise<VueWrapper> {
  if (result instanceof Error) {
    vi.mocked(simulate).mockRejectedValue(result)
  } else {
    vi.mocked(simulate).mockResolvedValue(result)
  }
  const stub = { template: '<div />' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: stub },
      { path: '/resultado', name: 'result', component: ResultView },
      { path: '/explorar', name: 'explore', component: stub },
      { path: '/fuentes', name: 'sources', component: stub },
    ],
  })
  await router.push(url)
  await router.isReady()
  const wrapper = mount(ResultView, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

beforeEach(() => vi.mocked(simulate).mockReset())

describe('pantalla de resultado', () => {
  it('pide la simulación con el ciclo, el grado y el curso de la URL', async () => {
    await show(recognized(), '/resultado?ciclo=CF-EJ&grado=UEJ-GP&curso=2025-26')
    expect(simulate).toHaveBeenCalledWith('CF-EJ', 'UEJ-GP', '2025-26')
  })

  it('muestra la cifra principal: ECTS reconocidos de los del grado, y su porcentaje', async () => {
    const wrapper = await show(recognized())

    expect(wrapper.get('[data-testid="ects-reconocidos"]').text()).toBe('16,5')
    expect(wrapper.get('[data-testid="resumen"]').text()).toContain('de 240 ECTS reconocidos')
    expect(wrapper.get('[data-testid="porcentaje"]').text()).toContain('6,88 %')
    expect(wrapper.get('[data-testid="porcentaje"]').text()).toContain('223,5 ECTS')
    expect(wrapper.get('h1').text()).toBe('Grado de Prueba en Informática Ficticia (Escuela de Ejemplo)')
    expect(wrapper.text()).toContain('Universidad de Ejemplo')
    expect(wrapper.text()).toContain('Norma Ficticia 01/2010')
  })

  it('la barra marca el tope aplicado', async () => {
    const wrapper = await show(recognized())
    const bar = wrapper.get('[data-testid="barra-ects"]')
    expect(bar.attributes('aria-label')).toBe('16,5 de 240 ECTS reconocidos. Tope aplicado: 60 ECTS.')
    expect(wrapper.get('[data-testid="barra-ects-tope"]').attributes('style')).toContain('left: 25%')
    expect(wrapper.get('[data-testid="barra-ects-relleno"]').attributes('style')).toContain('width: 6.875%')
  })

  it('el aviso de que es orientativo está siempre visible', async () => {
    for (const result of [recognized(), capped(), noData(), noAgreement()]) {
      const wrapper = await show(result)
      const notice = wrapper.get('[data-testid="aviso-orientativo"]').text()
      expect(notice).toContain('orientativo, no vinculante')
      expect(notice).toContain('universidad')
      expect(notice).toContain('tasas')
      wrapper.unmount()
    }
  })

  it('el botón de informe PDF descarga el mismo resultado: ciclo, grado y curso de la URL', async () => {
    for (const result of [recognized(), capped(), noData(), noAgreement()]) {
      const wrapper = await show(result, '/resultado?ciclo=CF-EJ&grado=UEJ-GP&curso=2025-26')
      const link = wrapper.get('[data-testid="descargar-pdf"]')
      expect(link.text()).toBe('Descargar informe PDF')
      expect(link.attributes('href')).toBe('/api/v1/simulations/report.pdf?cycle=CF-EJ&degree=UEJ-GP&year=2025-26')
      expect(link.attributes('download')).toBeDefined()
      wrapper.unmount()
    }
    const current = await show(recognized())
    expect(current.get('[data-testid="descargar-pdf"]').attributes('href')).toBe(
      '/api/v1/simulations/report.pdf?cycle=CF-EJ&degree=UEJ-GP',
    )
  })

  it('agrupa las asignaturas reconocidas por curso', async () => {
    const wrapper = await show(recognized())
    expect(wrapper.findAll('[data-testid="reconocida"]')).toHaveLength(2)
    const headings = wrapper.findAll('#reconocidas ~ div h3').map((h) => h.text())
    expect(headings).toEqual(['1.º curso', '2.º curso'])
    expect(wrapper.get('#reconocidas').text()).toBe('Asignaturas reconocidas')
    expect(wrapper.findAll('[data-testid="reconocida"]')[1].text()).toContain('4,5 ECTS')
  })

  it('«Ver fuente» muestra el documento, la página y el fragmento literal', async () => {
    const wrapper = await show(recognized())
    const subject = wrapper.findAll('[data-testid="reconocida"]')[0]
    const button = subject.get('button')
    const panel = subject.get('[data-testid="fuente"]')

    expect(button.text()).toContain('Ver fuente')
    expect(button.text()).toContain('de Programación de Prueba I')
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(panel.isVisible()).toBe(false)

    await button.trigger('click')

    expect(button.attributes('aria-expanded')).toBe('true')
    expect(button.attributes('aria-controls')).toBe(panel.attributes('id'))
    expect(panel.isVisible()).toBe(true)
    expect(panel.text()).toContain('Convenio Ficticio de Reconocimiento')
    expect(panel.text()).toContain('Convenio · 14 de marzo de 2025 · Página 3')
    expect(panel.get('blockquote').text()).toBe('«Módulo Ficticio 01 - Programación I»')
    expect(panel.get('a').attributes('href')).toBe('https://uej.example/docs/convenio-ficticio.pdf')
  })

  it('los créditos optativos y los condicionados van en secciones aparte', async () => {
    const wrapper = await show(recognized())
    expect(wrapper.get('[data-testid="optativos"]').text()).toContain('6 ECTS')
    const conditional = wrapper.get('[data-testid="condicionado"]')
    expect(conditional.text()).toContain('Prácticas Externas de Prueba')
    expect(conditional.text()).toContain('Condición: Solo si las prácticas son de naturaleza similar')
    expect(wrapper.text()).toContain('No están sumados')
  })

  it('una condicionada sin condición en el documento lo dice', async () => {
    const result = recognized()
    result.conditionalRecognitions = [{ ...result.conditionalRecognitions[0], conditionText: null }]
    const wrapper = await show(result)
    expect(wrapper.get('[data-testid="condicionado"]').text()).toContain(
      'Condición: condición no especificada en el documento',
    )
  })

  it('las asignaturas restantes son plegables, por curso', async () => {
    const wrapper = await show(recognized())
    const groups = wrapper.findAll('[data-testid="restantes-curso"]')
    expect(groups).toHaveLength(2)
    expect(groups[0].element.tagName).toBe('DETAILS')
    expect((groups[0].element as HTMLDetailsElement).open).toBe(false)
    expect(groups[0].get('summary').text()).toContain('1.º curso')
    expect(groups[1].get('summary').text()).toContain('Curso sin determinar')
    expect(groups[1].text()).toContain('ECTS sin determinar')
    expect(groups[0].text()).toContain('Matemática Ficticia I')
    expect(groups[0].text()).toContain('Básica')
  })

  it('si está limitado, explica cuánto suman las tablas, cuál es el tope y quién decide', async () => {
    const wrapper = await show(capped())

    const warning = wrapper.get('[data-testid="aviso-tope"]').text()
    expect(warning).toContain('Las tablas suman 66 ECTS')
    expect(warning).toContain('el máximo reconocible es 60 ECTS (el 25 % de la carga del grado)')
    expect(warning).toContain('La universidad decide qué asignaturas entran hasta ese límite')
    // Decisión acordada: la lista cambia de título y la cifra dice «reconocibles».
    expect(wrapper.get('#reconocidas').text()).toBe('Reconocibles hasta el tope de 60 ECTS')
    expect(wrapper.get('[data-testid="resumen"]').text()).toContain('de 240 ECTS reconocibles')
    expect(wrapper.get('[data-testid="ects-reconocidos"]').text()).toBe('60')
  })

  it('si el tope es el de la tabla, lo dice', async () => {
    const wrapper = await show(capped({ rulesTotalEcts: 36, recognizedEcts: 30, tableCapEcts: 30, appliedCapEcts: 30 }))
    expect(wrapper.get('[data-testid="aviso-tope"]').text()).toContain(
      'el máximo reconocible es 30 ECTS (el tope propio de esta tabla de reconocimiento)',
    )
  })

  it('sin limitar, no hay aviso de tope', async () => {
    const wrapper = await show(recognized())
    expect(wrapper.find('[data-testid="aviso-tope"]').exists()).toBe(false)
  })

  it('sin convenio y sin datos tienen mensajes distintos', async () => {
    const withoutAgreement = await show(noAgreement())
    const agreement = withoutAgreement.get('[data-testid="sin-convenio"]').text()
    expect(agreement).toContain('No existe tabla de reconocimiento firmada')
    expect(agreement).toContain('Lo hemos comprobado')
    expect(agreement).toContain('Comunicación de Ejemplo: inexistencia de tabla de reconocimiento')
    expect(withoutAgreement.find('[data-testid="sin-datos"]').exists()).toBe(false)
    expect(withoutAgreement.find('[data-testid="resumen"]').exists()).toBe(false)

    const withoutData = await show(noData())
    const data = withoutData.get('[data-testid="sin-datos"]').text()
    expect(data).toContain('Todavía no tenemos datos de esta combinación')
    expect(data).toContain('No significa que no haya convenio')
    expect(withoutData.find('[data-testid="sin-convenio"]').exists()).toBe(false)
    expect(data).not.toBe(agreement)
  })

  it('tiene un botón para copiar el enlace junto al de informe PDF', async () => {
    const wrapper = await show(recognized())
    const buttons = wrapper.findAll('button').map((b) => b.text())
    expect(buttons).toContain('Copiar enlace')
    expect(wrapper.get('[data-testid="descargar-pdf"]').text()).toBe('Descargar informe PDF')

    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Copiar enlace')!
      .trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith(window.location.href)
    expect(wrapper.text()).toContain('Enlace copiado.')
  })

  it('sin ciclo o sin grado en la URL, invita a elegirlos y no pide nada', async () => {
    const wrapper = await show(recognized(), '/resultado?ciclo=CF-EJ')
    expect(wrapper.get('h1').text()).toBe('Falta elegir ciclo y grado')
    expect(simulate).not.toHaveBeenCalled()
  })

  it('si la API falla, muestra su mensaje y permite reintentar', async () => {
    const wrapper = await show(
      new ApiError(404, 'No encontrado', 'No existe el grado «NADA» en los datos del curso 2026-27.'),
    )
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('No existe el grado «NADA»')

    vi.mocked(simulate).mockResolvedValue(recognized())
    await alert.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="ects-reconocidos"]').text()).toBe('16,5')
  })
})
