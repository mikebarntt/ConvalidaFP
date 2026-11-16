import AxeBuilder from '@axe-core/playwright'
import { expect, type Page, type TestInfo } from '@playwright/test'
import { strToU8, zipSync } from 'fflate'
import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'

const REPOSITORY = path.resolve(import.meta.dirname, '../..')

/** Valores del entorno de tests (.env.e2e): el administrador de prueba solo existe ahí. */
function readTestEnvironment(): Record<string, string> {
  const values: Record<string, string> = {}
  for (const line of readFileSync(path.join(REPOSITORY, '.env.e2e'), 'utf8').split('\n')) {
    const match = /^([A-Z_]+)=(.*)$/.exec(line.trim())
    if (match) {
      values[match[1]] = match[2]
    }
  }
  return values
}

const environment = readTestEnvironment()
export const ADMIN_USERNAME = environment.ADMIN_USERNAME
export const ADMIN_PASSWORD = environment.ADMIN_PASSWORD

/** Guarda una captura de la pantalla completa en docs/screenshots/<móvil o escritorio>/. */
export async function screenshot(page: Page, testInfo: TestInfo, name: string): Promise<void> {
  await page.screenshot({
    path: path.join(REPOSITORY, 'docs', 'screenshots', testInfo.project.name, `${name}.png`),
    fullPage: true,
    animations: 'disabled',
  })
}

/** Comprueba la pantalla con axe: ninguna infracción de WCAG 2.1 nivel A o AA. */
export async function expectAccessible(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  const summary = results.violations.map((v) => `${v.id}: ${v.help} (${v.nodes.map((n) => n.target).join(' | ')})`)
  expect(summary, 'infracciones de accesibilidad').toEqual([])
}

/** La pantalla cabe en el ancho del dispositivo: no hay que desplazarse en horizontal. */
export async function expectNoHorizontalScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  )
  expect(overflow, 'píxeles que sobresalen por la derecha').toBeLessThanOrEqual(0)
}

/**
 * Un ZIP con el dataset de ejemplo 2026-27 (ficticio), con cambios opcionales en sus ficheros.
 * El manifiesto se deja como está: si se cambia un fichero, su checksum dejará de coincidir, así
 * que para un dataset válido hay que pasar también el manifiesto corregido con `resign`.
 */
export async function sampleZip(changes: Record<string, [string, string]> = {}): Promise<Buffer> {
  const directory = path.join(REPOSITORY, 'data', 'sample', '2026-27')
  const files: Record<string, Uint8Array> = {}
  for (const name of readdirSync(directory)) {
    let content = readFileSync(path.join(directory, name), 'utf8')
    if (changes[name]) {
      const [search, replacement] = changes[name]
      expect(content, `texto a sustituir en ${name}`).toContain(search)
      content = content.replace(search, replacement)
    }
    files[name] = strToU8(content)
  }
  // Se recalculan los checksums del manifiesto para los ficheros cambiados.
  const manifest = JSON.parse(new TextDecoder().decode(files['manifest.json'])) as {
    files: { name: string; sha256: string }[]
  }
  for (const entry of manifest.files) {
    const digest = await crypto.subtle.digest('SHA-256', files[entry.name])
    entry.sha256 = Buffer.from(digest).toString('hex')
  }
  files['manifest.json'] = strToU8(JSON.stringify(manifest, null, 2) + '\n')
  return Buffer.from(zipSync(files))
}

/**
 * Pulsa un enlace del menú principal. En el móvil el menú está plegado en el botón «Menú»: primero
 * se abre.
 */
export async function clickMenuLink(page: Page, name: string): Promise<void> {
  const toggle = page.getByTestId('menu-movil')
  if ((await toggle.isVisible()) && (await toggle.getAttribute('aria-expanded')) !== 'true') {
    await toggle.click()
  }
  await page.getByRole('navigation').getByRole('link', { name, exact: true }).click()
}

/** Inicia sesión por la pantalla de login. */
export async function login(page: Page): Promise<void> {
  await page.getByLabel('Usuario').fill(ADMIN_USERNAME)
  await page.getByLabel('Contraseña').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Entrar' }).click()
}
