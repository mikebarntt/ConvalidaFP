import { expect, test } from '@playwright/test'
import {
  ADMIN_USERNAME,
  clickMenuLink,
  expectAccessible,
  expectNoHorizontalScroll,
  login,
  sampleZip,
  screenshot,
} from './support'

// Panel de administración: login, importaciones (con versiones) y auditoría.
// El administrador de prueba solo existe en el entorno de tests (.env.e2e).

const SIMULATION = '/resultado?ciclo=CF-PR&grado=UPR-GD'

test.describe('acceso', () => {
  test('sin sesión, las pantallas de admin llevan al login y después se vuelve a ellas', async ({ page }, testInfo) => {
    await page.goto('/admin/auditoria')

    await expect(page).toHaveURL(/\/admin\/login\?redirect=\/admin\/auditoria$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Iniciar sesión')
    await expect(page).toHaveTitle('Acceso de administración · ConvalidaFP')
    await expectAccessible(page)
    await expectNoHorizontalScroll(page)
    await screenshot(page, testInfo, '10-admin-login')

    await login(page)
    await expect(page).toHaveURL(/\/admin\/auditoria$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Auditoría')
    await expect(page.getByText(ADMIN_USERNAME).first()).toBeVisible()
  })

  test('credenciales incorrectas: mensaje en español y sin sesión', async ({ page }) => {
    await page.goto('/admin/login')
    await page.getByLabel('Usuario').fill('no-existe-e2e')
    await page.getByLabel('Contraseña').fill('una-clave-que-no-vale')
    await page.getByRole('button', { name: 'Entrar' }).click()

    await expect(page.getByRole('alert')).toHaveText('Usuario o contraseña incorrectos.')
    await expect(page).toHaveURL(/\/admin\/login$/)
    await expect(page.getByLabel('Contraseña')).toHaveValue('')

    await page.goto('/admin/importaciones')
    await expect(page).toHaveURL(/\/admin\/login/)
  })

  test('cerrar sesión', async ({ page }) => {
    await page.goto('/admin/importaciones')
    await login(page)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Importaciones')

    await page.getByRole('button', { name: 'Cerrar sesión' }).click()
    await expect(page).toHaveURL(/\/admin\/login$/)
    await page.goto('/admin/importaciones')
    await expect(page).toHaveURL(/\/admin\/login/)
  })
})

test.describe('importaciones', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/admin/importaciones')
    await login(page)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Importaciones')
  })

  test('un dataset con errores: se rechaza y muestra cada error con su fila', async ({ page }, testInfo) => {
    const zip = await sampleZip({ 'fp_modules.csv': [',14,1', ',0,1'] })
    await page
      .getByLabel('Fichero ZIP')
      .setInputFiles({ name: 'con-errores.zip', mimeType: 'application/zip', buffer: zip })
    await page.getByRole('button', { name: 'Subir y validar' }).click()

    await expect(page).toHaveURL(/\/admin\/importaciones\/\d+$/)
    await expect(page.getByTestId('resultado-importacion')).toContainText('Rechazada')
    await expect(page.getByTestId('resultado-importacion')).toContainText('no se ha guardado ningún dato')
    const error = page.getByTestId('errores').locator('tbody tr')
    await expect(error).toHaveCount(1)
    await expect(error).toContainText('V3')
    await expect(error).toContainText('fp_modules.csv')
    await expect(error).toContainText('Fila 2')
    await expect(error).toContainText('ects')
    await expect(error).toContainText('los ECTS deben ser mayores que 0')
    // Un dataset rechazado no se puede publicar.
    await expect(page.getByRole('button', { name: 'Publicar esta versión' })).toHaveCount(0)

    await expectAccessible(page)
    await screenshot(page, testInfo, '12-admin-importacion-rechazada')
  })

  test('subir, revisar, publicar y restaurar, siempre con confirmación', async ({ page, request }, testInfo) => {
    const original = (await (await request.get('/api/v1/dataset')).json()) as { versionId: number }
    await expectAccessible(page)
    await expectNoHorizontalScroll(page)
    await screenshot(page, testInfo, '11-admin-importaciones')

    // Subir una corrección del curso: el tope de la tabla T-PR-02 baja de 30 a 24.
    const zip = await sampleZip({ 'recognition_tables.csv': ['S-PR-01,30,', 'S-PR-01,24,'] })
    await page
      .getByLabel('Fichero ZIP')
      .setInputFiles({ name: 'correccion.zip', mimeType: 'application/zip', buffer: zip })
    await page.getByRole('button', { name: 'Subir y validar' }).click()

    // Informe: aceptada, avisos y diferencias con lo publicado.
    await expect(page).toHaveURL(/\/admin\/importaciones\/\d+$/)
    await expect(page.getByTestId('resultado-importacion')).toContainText('Aceptada')
    await expect(page.getByTestId('resultado-importacion')).toContainText('en estado borrador')
    await expect(page.getByTestId('avisos').locator('tbody tr')).toHaveCount(2)
    await expect(page.getByTestId('avisos')).toContainText('A1')
    await expect(page.getByTestId('avisos')).toContainText('A2')
    await expect(page.getByTestId('avisos')).toContainText('Fila 53')
    const diff = page.getByTestId('diferencias-fichero')
    await expect(diff).toHaveCount(1)
    await expect(diff).toContainText('recognition_tables.csv')
    await expect(diff).toContainText('0 altas · 1 cambios · 0 bajas')
    await expect(page.getByTestId('cambio')).toContainText('T-PR-02')
    await expect(page.getByTestId('cambio')).toContainText('table_max_ects')
    await expect(page.getByTestId('cambio').locator('del')).toHaveText('30')
    await expect(page.getByTestId('cambio').locator('ins')).toHaveText('24')
    await expectAccessible(page)
    await screenshot(page, testInfo, '13-admin-importacion-aceptada')

    // Importar no publica: el simulador sigue igual.
    const visitor = await page.context().newPage()
    await visitor.goto(SIMULATION)
    await expect(visitor.getByTestId('ects-reconocidos')).toHaveText('30')

    // Publicar pide confirmación; cancelar no hace nada.
    await page.getByRole('button', { name: 'Publicar esta versión' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toContainText('¿Publicar esta versión?')
    await expect(dialog).toContainText('quedará archivada y se podrá restaurar')
    await screenshot(page, testInfo, '14-admin-confirmar-publicacion')
    await dialog.getByRole('button', { name: 'Cancelar' }).click()
    await expect(dialog).toBeHidden()
    await visitor.reload()
    await expect(visitor.getByTestId('ects-reconocidos')).toHaveText('30')

    await page.getByRole('button', { name: 'Publicar esta versión' }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Sí, publicar' }).click()
    await expect(page.getByTestId('mensaje-publicar')).toContainText('ya es la versión vigente del curso 2026-27')
    await expect(page.getByTestId('resultado-importacion')).toContainText('en estado publicada')
    await expect(page.getByRole('button', { name: 'Publicar esta versión' })).toHaveCount(0)

    // El simulador ya usa la versión nueva.
    await visitor.reload()
    await expect(visitor.getByTestId('ects-reconocidos')).toHaveText('24')

    // Restaurar la versión anterior, también con confirmación.
    await clickMenuLink(page, 'Importaciones')
    const row = page.locator(`tr[data-version="${original.versionId}"]`)
    await expect(row).toContainText('Archivada')
    await row.getByRole('button', { name: /Restaurar/ }).click()
    const restore = page.getByRole('dialog')
    await expect(restore).toContainText('¿Restaurar esta versión?')
    await expect(restore).toContainText(`La versión ${original.versionId} volverá a ser la publicada del curso 2026-27`)
    await restore.getByRole('button', { name: 'Sí, restaurar' }).click()

    await expect(page.getByTestId('mensaje-restaurar')).toContainText(
      `La versión ${original.versionId} vuelve a ser la publicada`,
    )
    await expect(row).toContainText('Publicada')
    await expect(row.getByRole('button', { name: /Restaurar/ })).toHaveCount(0)
    await visitor.reload()
    await expect(visitor.getByTestId('ects-reconocidos')).toHaveText('30')
    await screenshot(page, testInfo, '15-admin-versiones')
  })

  test('errores de subida en español: sin fichero, no es un dataset y demasiado grande (413)', async ({
    page,
    request,
  }) => {
    await page.getByRole('button', { name: 'Subir y validar' }).click()
    await expect(page.getByRole('alert')).toHaveText('Elige antes un fichero ZIP.')

    // Más de 25 MB: lo corta Nginx, con el mismo formato de error que la API.
    const tooLarge = Buffer.alloc(26 * 1024 * 1024, 1)
    await page
      .getByLabel('Fichero ZIP')
      .setInputFiles({ name: 'enorme.zip', mimeType: 'application/zip', buffer: tooLarge })
    await page.getByRole('button', { name: 'Subir y validar' }).click()
    await expect(page.getByRole('alert')).toHaveText('El fichero supera el tamaño máximo de 25 MB.')
    await expect(page).toHaveURL(/\/admin\/importaciones$/)

    const response = await request.post('/api/v1/admin/imports', {
      multipart: { file: { name: 'enorme.zip', mimeType: 'application/zip', buffer: tooLarge } },
    })
    expect(response.status()).toBe(413)
    expect(response.headers()['content-type']).toContain('application/problem+json')
    expect(await response.json()).toEqual({
      type: 'about:blank',
      title: 'Fichero demasiado grande',
      status: 413,
      detail: 'El fichero supera el tamaño máximo de 25 MB.',
    })

    // Un fichero que no es un dataset: se rechaza con su informe.
    await page.getByLabel('Fichero ZIP').setInputFiles({
      name: 'notas.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('esto no es un dataset'),
    })
    await page.getByRole('button', { name: 'Subir y validar' }).click()
    await expect(page.getByTestId('resultado-importacion')).toContainText('Rechazada')
    await expect(page.getByTestId('errores').locator('tbody tr')).toHaveCount(10)
    await expect(page.getByTestId('errores')).toContainText('falta el fichero')
  })

  test('si la sesión caduca, vuelve al login y después a la pantalla en la que estaba', async ({ page }, testInfo) => {
    await clickMenuLink(page, 'Auditoría')
    await expect(page.getByTestId('auditoria')).toBeVisible()

    // La sesión caduca en el servidor: desaparece la cookie.
    await page.context().clearCookies()
    await clickMenuLink(page, 'Importaciones')

    await expect(page).toHaveURL(/\/admin\/login\?redirect=\/admin\/importaciones&caducada=1$/)
    await expect(page.getByTestId('caducada')).toContainText('Tu sesión ha caducado')
    await screenshot(page, testInfo, '17-admin-sesion-caducada')

    await login(page)
    await expect(page).toHaveURL(/\/admin\/importaciones$/)
    await expect(page.getByTestId('importaciones')).toBeVisible()
    // Tras volver a entrar, las operaciones con token CSRF siguen funcionando.
    await page.getByRole('button', { name: 'Subir y validar' }).click()
    await expect(page.getByRole('alert')).toHaveText('Elige antes un fichero ZIP.')
  })
})

test.describe('auditoría', () => {
  test('registra quién hizo qué, sobre qué versión y cuándo', async ({ page }, testInfo) => {
    await page.goto('/admin/auditoria')
    await login(page)

    const table = page.getByTestId('auditoria')
    await expect(table).toBeVisible()
    for (const action of ['IMPORTAR', 'PUBLICAR', 'RESTAURAR', 'LOGIN_FALLIDO']) {
      await expect(table.locator(`tr[data-action="${action}"]`).first()).toBeVisible()
    }
    const published = table.locator('tr[data-action="PUBLICAR"]').first()
    await expect(published).toContainText(ADMIN_USERNAME)
    await expect(published).toContainText('Publicación')
    await expect(published).toContainText('2026-27')
    await expect(table.locator('tr[data-action="LOGIN_FALLIDO"]').first()).toContainText('no-existe-e2e')
    await expect(page.getByText('no se puede modificar ni borrar')).toBeVisible()

    await expectAccessible(page)
    await screenshot(page, testInfo, '16-admin-auditoria')
  })
})
