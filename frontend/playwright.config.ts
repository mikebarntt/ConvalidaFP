import { defineConfig, devices } from '@playwright/test'

/**
 * Tests de extremo a extremo, en móvil y en escritorio, contra la aplicación completa arrancada con
 * Docker Compose y el entorno de tests (.env.e2e). Cómo lanzarlos: ver el README.
 *
 * Se ejecutan de uno en uno: los tests de administración publican y restauran versiones, así que
 * comparten estado.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  outputDir: './test-results',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3100',
    locale: 'es-ES',
    timezoneId: 'Europe/Madrid',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'escritorio', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
    { name: 'movil', use: { ...devices['Pixel 7'] } },
  ],
})
