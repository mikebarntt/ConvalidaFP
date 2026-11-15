<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getImport, publishImport } from '@/api/admin'
import { errorMessage } from '@/api/client'
import type { ImportSummary, ValidationIssue } from '@/api/types'
import AsyncState from '@/components/AsyncState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { formatDateTime, importOutcomeLabel, versionStatusLabel } from '@/lib/format'
import { useAsyncData } from '@/lib/useAsyncData'

// Informe de una importación: errores por fila, avisos, diferencias con lo publicado y publicación.
const route = useRoute()
const importId = computed(() => Number(route.params.id))

const detail = useAsyncData<ImportSummary>(() => getImport(importId.value), importId)
const item = computed(() => detail.data.value)
const report = computed(() => item.value?.report ?? null)

/** Ficheros con alguna diferencia. */
const changedFiles = computed(
  () => report.value?.diff?.files.filter((f) => f.added.length || f.removed.length || f.changed.length) ?? [],
)

const confirming = ref(false)
const publishing = ref(false)
const publishMessage = ref<{ kind: 'ok' | 'error'; text: string } | null>(null)

async function publish(): Promise<void> {
  publishing.value = true
  try {
    const published = await publishImport(importId.value)
    detail.data.value = published
    publishMessage.value = {
      kind: 'ok',
      text: `Publicada: ya es la versión vigente del curso ${published.academicYear}.`,
    }
  } catch (cause) {
    publishMessage.value = { kind: 'error', text: errorMessage(cause) }
  } finally {
    publishing.value = false
    confirming.value = false
  }
}

const issueLocation = (issue: ValidationIssue) => (issue.row === null ? 'Fichero entero' : `Fila ${issue.row}`)
</script>

<template>
  <section aria-labelledby="titulo">
    <p class="eyebrow">
      <RouterLink :to="{ name: 'admin-imports' }">Importaciones</RouterLink>
    </p>
    <h1 id="titulo" class="mt-1 text-3xl font-extrabold tracking-tight">Importación {{ importId }}</h1>

    <AsyncState :loading="detail.loading.value" :error="detail.error.value" class="mt-6" @retry="detail.reload">
      <div v-if="item && report">
        <div
          class="mt-6 rounded-md border p-4"
          :class="item.outcome === 'RECHAZADA' ? 'border-danger bg-danger-soft' : 'border-brand bg-brand-soft'"
          data-testid="resultado-importacion"
        >
          <p class="text-lg font-bold">
            {{ importOutcomeLabel(item.outcome) }}
            <span v-if="item.versionStatus" class="font-normal">
              · versión {{ item.datasetVersionId }} en estado {{ versionStatusLabel(item.versionStatus).toLowerCase() }}
            </span>
          </p>
          <p class="mt-1">
            <template v-if="item.outcome === 'RECHAZADA'">
              El dataset tiene {{ item.errorCount }} {{ item.errorCount === 1 ? 'error' : 'errores' }} y no se ha
              guardado ningún dato. Corrige los ficheros y vuelve a subirlo.
            </template>
            <template v-else>
              El dataset es válido{{
                item.warningCount ? `, con ${item.warningCount} avisos para revisar a mano` : ''
              }}.
            </template>
          </p>
        </div>

        <dl class="mt-4 grid gap-x-8 gap-y-2 text-sm sm:grid-cols-4">
          <div>
            <dt class="eyebrow">Fichero</dt>
            <dd class="mt-1 break-all">{{ item.fileName ?? '—' }}</dd>
          </div>
          <div>
            <dt class="eyebrow">Curso</dt>
            <dd class="tabular mt-1">{{ item.academicYear ?? 'No se pudo leer' }}</dd>
          </div>
          <div>
            <dt class="eyebrow">Subido por</dt>
            <dd class="mt-1">{{ item.createdBy }}</dd>
          </div>
          <div>
            <dt class="eyebrow">Fecha</dt>
            <dd class="mt-1">{{ formatDateTime(item.createdAt) }}</dd>
          </div>
        </dl>

        <p
          v-if="publishMessage"
          :role="publishMessage.kind === 'error' ? 'alert' : 'status'"
          class="mt-4 rounded-md border p-3"
          :class="
            publishMessage.kind === 'error' ? 'border-danger bg-danger-soft text-danger' : 'border-brand bg-brand-soft'
          "
          data-testid="mensaje-publicar"
        >
          {{ publishMessage.text }}
        </p>
        <div v-if="item.versionStatus === 'BORRADOR'" class="mt-4">
          <button type="button" class="btn btn-primary" @click="confirming = true">Publicar esta versión</button>
        </div>

        <section v-if="report.errors.length" class="mt-10" aria-labelledby="errores">
          <h2 id="errores" class="text-xl font-bold">Errores ({{ report.errors.length }})</h2>
          <p class="mt-1 text-sm text-muted">Cada error indica el fichero, la fila y la columna donde está.</p>
          <div
            class="card relative mt-3 overflow-x-auto"
            role="region"
            aria-label="Tabla desplazable en horizontal"
            tabindex="0"
          >
            <table class="data-table" data-testid="errores">
              <caption class="sr-only">
                Errores de validación
              </caption>
              <thead>
                <tr>
                  <th scope="col">Código</th>
                  <th scope="col">Fichero</th>
                  <th scope="col">Fila</th>
                  <th scope="col">Columna</th>
                  <th scope="col">Motivo</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(issue, index) in report.errors" :key="index">
                  <td class="tabular font-semibold text-danger">{{ issue.code }}</td>
                  <td class="break-all">{{ issue.file }}</td>
                  <td class="tabular whitespace-nowrap">{{ issueLocation(issue) }}</td>
                  <td class="break-all">{{ issue.column ?? '—' }}</td>
                  <td>{{ issue.message }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section v-if="report.warnings.length" class="mt-10" aria-labelledby="avisos">
          <h2 id="avisos" class="text-xl font-bold">Avisos ({{ report.warnings.length }})</h2>
          <p class="mt-1 text-sm text-muted">No bloquean la carga, pero conviene revisarlos antes de publicar.</p>
          <div
            class="card relative mt-3 overflow-x-auto"
            role="region"
            aria-label="Tabla desplazable en horizontal"
            tabindex="0"
          >
            <table class="data-table" data-testid="avisos">
              <caption class="sr-only">
                Avisos de validación
              </caption>
              <thead>
                <tr>
                  <th scope="col">Código</th>
                  <th scope="col">Fichero</th>
                  <th scope="col">Fila</th>
                  <th scope="col">Columna</th>
                  <th scope="col">Motivo</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(issue, index) in report.warnings" :key="index">
                  <td class="tabular font-semibold text-cap">{{ issue.code }}</td>
                  <td class="break-all">{{ issue.file }}</td>
                  <td class="tabular whitespace-nowrap">{{ issueLocation(issue) }}</td>
                  <td class="break-all">{{ issue.column ?? '—' }}</td>
                  <td>{{ issue.message }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section v-if="report.diff" class="mt-10" aria-labelledby="diferencias">
          <h2 id="diferencias" class="text-xl font-bold">Diferencias con lo publicado</h2>
          <p class="mt-1 text-sm text-muted">
            <template v-if="report.diff.baselineVersionId">
              Comparado con la versión {{ report.diff.baselineVersionId }} (curso
              {{ report.diff.baselineAcademicYear }}).
            </template>
            <template v-else>No había ninguna versión publicada: todo son altas.</template>
          </p>
          <p v-if="!changedFiles.length" class="card mt-3 p-4" data-testid="sin-diferencias">
            No hay ninguna diferencia con lo publicado.
          </p>
          <div v-for="file in changedFiles" :key="file.file" class="card mt-3 p-4" data-testid="diferencias-fichero">
            <h3 class="font-bold">{{ file.file }}</h3>
            <p class="tabular mt-1 text-sm text-muted">
              {{ file.added.length }} altas · {{ file.changed.length }} cambios · {{ file.removed.length }} bajas
            </p>

            <details v-if="file.added.length" class="mt-2">
              <summary class="min-h-11 py-2 font-semibold">Altas ({{ file.added.length }})</summary>
              <p class="break-words text-sm">{{ file.added.join(', ') }}</p>
            </details>
            <details v-if="file.removed.length" class="mt-2">
              <summary class="min-h-11 py-2 font-semibold">Bajas ({{ file.removed.length }})</summary>
              <p class="break-words text-sm">{{ file.removed.join(', ') }}</p>
            </details>
            <div v-if="file.changed.length" class="mt-2">
              <p class="font-semibold">Cambios</p>
              <ul class="mt-1 grid gap-2 text-sm">
                <li v-for="change in file.changed" :key="change.id" data-testid="cambio">
                  <span class="font-semibold">{{ change.id }}</span>
                  <ul class="mt-1 grid gap-1">
                    <li v-for="field in change.fields" :key="field.column">
                      <span class="text-muted">{{ field.column }}:</span>
                      <del class="mx-1 bg-danger-soft px-1 text-danger">{{ field.before || '(vacío)' }}</del>
                      <span aria-hidden="true">→</span>
                      <span class="sr-only">pasa a</span>
                      <ins class="mx-1 bg-brand-soft px-1 no-underline">{{ field.after || '(vacío)' }}</ins>
                    </li>
                  </ul>
                </li>
              </ul>
            </div>
          </div>
        </section>
      </div>
    </AsyncState>

    <ConfirmDialog
      :open="confirming"
      title="¿Publicar esta versión?"
      confirm-label="Sí, publicar"
      :busy="publishing"
      @confirm="publish"
      @cancel="confirming = false"
    >
      <p v-if="item">
        Pasará a ser la versión vigente del curso {{ item.academicYear }} y el simulador empezará a usarla de inmediato.
        La versión publicada actualmente quedará archivada y se podrá restaurar.
      </p>
    </ConfirmDialog>
  </section>
</template>
