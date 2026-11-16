import { expect, test } from '@playwright/test'
import { expectAccessible, expectNoHorizontalScroll, screenshot } from './support'

// Pantallas públicas: inicio, resultado, comparador, explorador y fuentes.

test.describe('flujo principal: simulación y resultado', () => {
  test('de elegir ciclo y grado al resultado, con su fuente', async ({ page }, testInfo) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toContainText('De tu ciclo de FP al grado')
    await expect(page).toHaveTitle('Inicio · ConvalidaFP')
    await expectAccessible(page)
    await expectNoHorizontalScroll(page)
    await screenshot(page, testInfo, '01-inicio')

    // 1. El ciclo. Hay dos con el mismo nombre y distinta normativa: se distinguen por ella, y cada
    // uno explica en una frase cómo saber si es el tuyo (help_text del dataset).
    await expect(page.getByText('Norma Ficticia 01/2010', { exact: true })).toBeVisible()
    await expect(page.getByText('Norma Ficticia 02/2024', { exact: true })).toBeVisible()
    const help = page.getByTestId('ayuda-ciclo')
    await expect(help).toHaveCount(2)
    await expect(help.first()).toHaveText(
      'Si tu título cita la Norma Ficticia 01/2010 (planes anteriores a 2024), este es tu ciclo.',
    )
    await expect(help.nth(1)).toContainText('Norma Ficticia 02/2024')
    await page.locator('input[name="ciclo"][value="CF-EJ"]').check()

    // 2. Universidad y grado. Cada grado dice si tiene tabla para ese ciclo.
    await page.getByLabel('Universidad').selectOption('UEJ')
    const degree = page.getByLabel('Grado', { exact: true })
    await expect(degree.locator('option[value="UEJ-GP"]')).toContainText('con tabla de reconocimiento')
    await degree.selectOption('UEJ-GP')
    await screenshot(page, testInfo, '02-inicio-elegido')
    await page.getByRole('button', { name: 'Ver resultado' }).click()

    // El estado vive en la URL.
    await expect(page).toHaveURL(/\/resultado\?ciclo=CF-EJ&grado=UEJ-GP$/)
    await expect(page).toHaveTitle('Resultado · ConvalidaFP')
    await expect(page.getByTestId('ects-reconocidos')).toHaveText('51')
    await expect(page.getByTestId('resumen')).toContainText('de 240 ECTS reconocidos')
    await expect(page.getByTestId('porcentaje')).toContainText('21,25 %')
    await expect(page.getByTestId('porcentaje')).toContainText('189 ECTS')
    await expect(page.getByTestId('barra-ects')).toHaveAttribute(
      'aria-label',
      '51 de 240 ECTS reconocidos. Tope aplicado: 60 ECTS.',
    )
    await expect(page.getByTestId('aviso-tope')).toHaveCount(0)

    // El aviso de que es orientativo, siempre visible.
    await expect(page.getByTestId('aviso-orientativo')).toContainText('Resultado orientativo, no vinculante')
    await expect(page.getByTestId('aviso-orientativo')).toContainText('tasas')

    // Reconocidas por curso, optativos y condicionados aparte.
    await expect(page.getByRole('heading', { name: 'Asignaturas reconocidas' })).toBeVisible()
    await expect(page.getByTestId('reconocida')).toHaveCount(8)
    await expect(page.getByRole('heading', { name: '1.º curso' }).first()).toBeVisible()
    await expect(page.getByTestId('optativos')).toContainText('6 ECTS')
    await expect(page.getByTestId('condicionado')).toContainText('Solo si las prácticas son de naturaleza similar')

    // «Ver fuente»: documento, página y fragmento literal.
    const web = page.getByTestId('reconocida').filter({ hasText: 'Desarrollo Web de Prueba' })
    await web.getByRole('button', { name: /Ver fuente/ }).click()
    const source = web.getByTestId('fuente')
    await expect(source).toContainText('Convenio Ficticio de Reconocimiento')
    await expect(source).toContainText('Página 3')
    await expect(source).toContainText('«Módulo Ficticio 07 - Desarrollo Web»')
    await expect(source.getByRole('link')).toHaveAttribute('href', 'https://uej.example/docs/convenio-ficticio.pdf')

    // Asignaturas restantes, plegables.
    const thirdYear = page.getByTestId('restantes-curso').filter({ hasText: '3.º curso' })
    await expect(thirdYear.getByText('Inteligencia Artificial Ficticia')).toBeHidden()
    await thirdYear.locator('summary').click()
    await expect(thirdYear.getByText('Inteligencia Artificial Ficticia')).toBeVisible()

    // Informe PDF del mismo resultado.
    const pdf = page.getByRole('link', { name: 'Descargar informe PDF' })
    await expect(pdf).toHaveAttribute('href', '/api/v1/simulations/report.pdf?cycle=CF-EJ&degree=UEJ-GP')
    const response = await page.request.get('/api/v1/simulations/report.pdf?cycle=CF-EJ&degree=UEJ-GP')
    expect(response.status()).toBe(200)
    expect(response.headers()['content-type']).toBe('application/pdf')
    expect((await response.body()).subarray(0, 5).toString()).toBe('%PDF-')
    const download = page.waitForEvent('download')
    await pdf.click()
    expect((await download).suggestedFilename()).toBe('convalidafp-CF-EJ-UEJ-GP.pdf')

    await expectAccessible(page)
    await expectNoHorizontalScroll(page)
    await screenshot(page, testInfo, '03-resultado')
  })

  test('copiar el enlace: lleva al mismo resultado', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])
    await page.goto('/resultado?ciclo=CF-EJ&grado=UEJ-GP')
    await expect(page.getByTestId('ects-reconocidos')).toHaveText('51')

    await page.getByRole('button', { name: 'Copiar enlace' }).click()
    await expect(page.getByText('Enlace copiado.')).toBeVisible()
    const copied = await page.evaluate(() => navigator.clipboard.readText())
    expect(copied).toBe(page.url())

    // Quien recibe el enlace ve el mismo resultado sin elegir nada.
    const other = await context.newPage()
    await other.goto(copied)
    await expect(other.getByTestId('ects-reconocidos')).toHaveText('51')
  })

  test('resultado limitado por el tope: lo explica en una frase', async ({ page }, testInfo) => {
    await page.goto('/resultado?ciclo=CF-EJ&grado=UPR-GD')

    await expect(page.getByTestId('ects-reconocidos')).toHaveText('60')
    await expect(page.getByTestId('resumen')).toContainText('de 240 ECTS reconocibles')
    const warning = page.getByTestId('aviso-tope')
    await expect(warning).toContainText('Las tablas suman 66 ECTS')
    await expect(warning).toContainText('el máximo reconocible es 60 ECTS (el 25 % de la carga del grado)')
    await expect(warning).toContainText('La universidad decide qué asignaturas entran hasta ese límite')
    await expect(page.getByRole('heading', { name: 'Reconocibles hasta el tope de 60 ECTS' })).toBeVisible()
    await expect(page.getByTestId('reconocida')).toHaveCount(9)

    await expectAccessible(page)
    await expectNoHorizontalScroll(page)
    await screenshot(page, testInfo, '04-resultado-limitado')

    // Tope propio de la tabla, inferior al 25 %.
    await page.goto('/resultado?ciclo=CF-PR&grado=UPR-GD')
    await expect(page.getByTestId('ects-reconocidos')).toHaveText('30')
    await expect(page.getByTestId('aviso-tope')).toContainText('el tope propio de esta tabla de reconocimiento')
  })

  test('sin convenio y sin datos: mensajes distintos y claros', async ({ page }, testInfo) => {
    await page.goto('/resultado?ciclo=CF-EJ&grado=UFI-GS')
    const noAgreement = page.getByTestId('sin-convenio')
    await expect(noAgreement).toContainText('No existe tabla de reconocimiento firmada')
    await expect(noAgreement).toContainText('Lo hemos comprobado')
    await expect(page.getByTestId('sin-datos')).toHaveCount(0)
    await expect(page.getByTestId('aviso-orientativo')).toBeVisible()
    await expectAccessible(page)
    await screenshot(page, testInfo, '05-resultado-sin-convenio')

    await page.goto('/resultado?ciclo=CF-PR&grado=UEJ-GP')
    const noData = page.getByTestId('sin-datos')
    await expect(noData).toContainText('Todavía no tenemos datos de esta combinación')
    await expect(noData).toContainText('No significa que no haya convenio')
    await expect(page.getByTestId('sin-convenio')).toHaveCount(0)
    await expect(page.getByTestId('aviso-orientativo')).toBeVisible()
    await expectAccessible(page)
    await screenshot(page, testInfo, '06-resultado-sin-datos')
  })

  test('condicionado sin condición en el documento, y decimales', async ({ page }) => {
    await page.goto('/resultado?ciclo=CF-EJ&grado=UEJ-GP-SUR')
    await expect(page.getByTestId('ects-reconocidos')).toHaveText('22,5')
    await expect(page.getByTestId('condicionado')).toContainText('condición no especificada en el documento')
    await expect(page.getByTestId('optativos')).toContainText('4,5 ECTS')

    // subjects_complete = false: sin lista de restantes; ECTS pendientes y enlace a la web del grado.
    await expect(page.getByTestId('restantes-curso')).toHaveCount(0)
    const plan = page.getByTestId('plan-incompleto')
    await expect(plan).toContainText('Te quedan 217,5 ECTS por cursar')
    await expect(plan.getByRole('link', { name: 'la web de la universidad' })).toHaveAttribute(
      'href',
      'https://uej.example/sur/grados/prueba',
    )
    await expectAccessible(page)
  })

  test('errores en español: grado inexistente y resultado sin elegir', async ({ page }) => {
    await page.goto('/resultado?ciclo=CF-EJ&grado=NO-EXISTE')
    await expect(page.getByRole('alert')).toContainText('No existe el grado «NO-EXISTE»')
    await expect(page.getByRole('button', { name: 'Volver a intentarlo' })).toBeVisible()

    await page.goto('/resultado')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Falta elegir ciclo y grado')

    await page.goto('/una-pagina-que-no-existe')
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Esta página no existe')
  })
})

test.describe('comparador, explorador y fuentes', () => {
  test('comparador: un ciclo frente a 4 grados, lado a lado', async ({ page }, testInfo) => {
    await page.goto('/comparar')
    await page.getByLabel('Ciclo de FP').selectOption('CF-EJ')
    for (const degree of [
      'Grado de Prueba en Informática Ficticia (Escuela de Ejemplo)',
      'Doble Grado de Prueba en Informática Ficticia y Gestión de Ejemplo (Escuela de Ejemplo)',
      'Grado de Demostración en Datos Ficticios (Facultad de Prueba)',
      'Grado Ficticio en Software, Sistemas y "Cosas" de Ejemplo',
    ]) {
      await page.getByLabel(degree, { exact: true }).check()
    }
    // Como máximo 4: el quinto no se puede marcar.
    await expect(page.getByText('4 de 4 elegidos')).toBeVisible()
    await expect(page.getByLabel(/Campus Sur/)).toBeDisabled()
    await page.getByRole('button', { name: 'Comparar' }).click()

    await expect(page).toHaveURL(/\/comparar\?ciclo=CF-EJ&grados=/)
    const cards = page.getByTestId('comparado')
    await expect(cards).toHaveCount(4)
    await expect(cards.filter({ hasText: 'Doble Grado' })).toContainText('75')
    await expect(cards.filter({ hasText: 'Demostración' })).toContainText('Limitado por el tope')
    await expect(cards.filter({ hasText: 'Software' })).toContainText('No existe tabla de reconocimiento firmada')
    await expect(page.getByTestId('aviso-orientativo')).toBeVisible()

    await expectAccessible(page)
    await expectNoHorizontalScroll(page)
    await screenshot(page, testInfo, '07-comparador')

    // El enlace se puede compartir: al abrirlo, la comparación y la selección están ahí.
    await page.goto('/comparar?ciclo=CF-EJ&grados=UEJ-GP,UEJ-DG')
    await expect(page.getByTestId('comparado')).toHaveCount(2)
    await expect(page.getByLabel('Ciclo de FP')).toHaveValue('CF-EJ')
    await page
      .getByTestId('comparado')
      .first()
      .getByRole('link', { name: /Ver detalle/ })
      .click()
    await expect(page).toHaveURL(/\/resultado\?ciclo=CF-EJ&grado=UEJ-GP$/)
  })

  test('explorador: grados que reconocen algo, de más a menos ECTS', async ({ page }, testInfo) => {
    await page.goto('/')
    await page.locator('input[name="ciclo"][value="CF-EJ"]').check()
    await page.getByTestId('ver-todos').click()

    await expect(page).toHaveURL(/\/explorar\?ciclo=CF-EJ$/)
    const entries = page.getByTestId('explorado')
    await expect(entries).toHaveCount(4)
    await expect(entries.nth(0)).toContainText('Doble Grado de Prueba')
    await expect(entries.nth(0)).toContainText('75')
    await expect(entries.nth(1)).toContainText('Grado de Demostración')
    await expect(entries.nth(1)).toContainText('Limitado por el tope: las tablas suman 66 ECTS')
    await expect(entries.nth(3)).toContainText('22,5')
    await expect(page.getByTestId('aviso-orientativo')).toBeVisible()

    await expectAccessible(page)
    await expectNoHorizontalScroll(page)
    await screenshot(page, testInfo, '08-explorador')

    // Cambiar de ciclo actualiza la URL y el resultado.
    await page.getByLabel('Ciclo de FP').selectOption('CF-PR')
    await expect(page).toHaveURL(/\/explorar\?ciclo=CF-PR$/)
    await expect(page.getByTestId('explorado')).toHaveCount(1)
    await page.getByTestId('explorado').getByRole('link').click()
    await expect(page).toHaveURL(/\/resultado\?ciclo=CF-PR&grado=UPR-GD$/)
  })

  test('fuentes y metodología: documentos, última actualización y cálculo', async ({ page }, testInfo) => {
    await page.goto('/fuentes')

    await expect(page.getByTestId('actualizacion')).toContainText('2026-27')
    await expect(page.getByTestId('actualizacion')).toContainText('Última actualización')
    await expect(page.getByTestId('documento')).toHaveCount(6)
    await expect(page.getByTestId('documento').first()).toContainText('Convenio')
    await expect(page.getByTestId('documento').first()).toContainText('Publicado el 14 de marzo de 2025')
    await expect(page.getByRole('heading', { name: 'Cómo se calcula el resultado' })).toBeVisible()
    await expect(page.getByText('25 % de la carga del grado')).toBeVisible()
    await expect(page.getByText('El resultado es orientativo, no vinculante.')).toBeVisible()

    await expectAccessible(page)
    await expectNoHorizontalScroll(page)
    await screenshot(page, testInfo, '09-fuentes')
  })
})

test.describe('menú principal', () => {
  test('en el móvil cabe en una línea y se despliega con el botón «Menú»', async ({ page }, testInfo) => {
    await page.goto('/')
    const toggle = page.getByTestId('menu-movil')
    const menu = page.getByRole('navigation', { name: 'Principal' })
    const header = page.locator('header')

    if (testInfo.project.name === 'escritorio') {
      await expect(toggle).toBeHidden()
      await expect(menu.getByRole('link', { name: 'Comparar' })).toBeVisible()
      return
    }

    // Plegado: la cabecera es una sola línea con la marca y el botón.
    await expect(menu.getByRole('link', { name: 'Comparar' })).toBeHidden()
    const brand = await page
      .getByRole('link', { name: /ConvalidaFP/ })
      .first()
      .boundingBox()
    const button = await toggle.boundingBox()
    expect(Math.abs(brand!.y + brand!.height / 2 - (button!.y + button!.height / 2))).toBeLessThan(8)
    expect((await header.boundingBox())!.height).toBeLessThan(80)

    // Desplegado: los cuatro enlaces, sin desbordar y accesible.
    await toggle.click()
    await expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await expect(menu.getByRole('link')).toHaveCount(4)
    await expectAccessible(page)
    await expectNoHorizontalScroll(page)

    // Escape lo cierra y devuelve el foco al botón; elegir un enlace navega y lo cierra.
    await menu.getByRole('link', { name: 'Comparar' }).focus()
    await page.keyboard.press('Escape')
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await expect(toggle).toBeFocused()
    await toggle.click()
    await menu.getByRole('link', { name: 'Explorar' }).click()
    await expect(page).toHaveURL(/\/explorar/)
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  })
})

test.describe('teclado y privacidad', () => {
  test('se puede simular solo con el teclado', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('input[name="ciclo"]').first()).toBeVisible()

    // El primer tabulador lleva al enlace para saltar al contenido.
    await page.keyboard.press('Tab')
    await expect(page.getByRole('link', { name: 'Saltar al contenido' })).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.locator('#contenido')).toBeFocused()

    // Ciclo: con el foco en el grupo, la barra espaciadora marca la opción.
    await page.locator('input[name="ciclo"][value="CF-EJ"]').focus()
    await page.keyboard.press('Space')
    await page.keyboard.press('Tab')
    await expect(page.getByLabel('Universidad')).toBeFocused()
    await page.getByLabel('Universidad').selectOption('UEJ')
    // El desplegable de grados se activa en cuanto llegan los grados de esa universidad.
    await expect(page.getByLabel('Grado', { exact: true })).toBeEnabled()
    await page.keyboard.press('Tab')
    await expect(page.getByLabel('Grado', { exact: true })).toBeFocused()
    await page.getByLabel('Grado', { exact: true }).selectOption('UEJ-GP')
    await page.keyboard.press('Tab')
    await expect(page.getByRole('button', { name: 'Ver resultado' })).toBeFocused()
    await page.keyboard.press('Enter')

    await expect(page.getByTestId('ects-reconocidos')).toHaveText('51')
    // Al cambiar de pantalla, el foco pasa al contenido nuevo.
    await expect(page.locator('#contenido')).toBeFocused()
  })

  test('simular no deja cookies ni llama a terceros', async ({ page, context, baseURL }) => {
    const ownOrigin = new URL(baseURL!).origin
    const externalRequests: string[] = []
    page.on('request', (request) => {
      if (new URL(request.url()).origin !== ownOrigin) {
        externalRequests.push(request.url())
      }
    })
    await page.goto('/resultado?ciclo=CF-EJ&grado=UEJ-GP')
    await expect(page.getByTestId('ects-reconocidos')).toHaveText('51')
    await page.goto('/explorar?ciclo=CF-EJ')
    await expect(page.getByTestId('explorado')).toHaveCount(4)

    expect(await context.cookies()).toEqual([])
    expect(externalRequests).toEqual([])
  })
})
