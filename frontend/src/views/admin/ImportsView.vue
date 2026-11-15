<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { listImports, listVersions, restoreVersion, uploadDataset } from '@/api/admin'
import { errorMessage } from '@/api/client'
import type { DatasetVersion } from '@/api/types'
import AsyncState from '@/components/AsyncState.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { formatDateTime, importOutcomeLabel, versionStatusLabel } from '@/lib/format'
import { useAsyncData } from '@/lib/useAsyncData'

// Importaciones: subir un ZIP, ver las importaciones hechas y restaurar versiones archivadas.
const router = useRouter()

const imports = useAsyncData(listImports)
const versions = useAsyncData(listVersions)

// ---------- subir un dataset ----------

const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadError = ref<string | null>(null)

function onFileChange(event: Event): void {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
  uploadError.value = null
}

async function upload(): Promise<void> {
  if (!selectedFile.value) {
    uploadError.value = 'Elige antes un fichero ZIP.'
    return
  }
  uploading.value = true
  uploadError.value = null
  try {
    const created = await uploadDataset(selectedFile.value)
    await router.push({ name: 'admin-import', params: { id: created.id } })
  } catch (cause) {
    uploadError.value = errorMessage(cause)
  } finally {
    uploading.value = false
  }
}

// ---------- restaurar una versión ----------

const toRestore = ref<DatasetVersion | null>(null)
const restoring = ref(false)
const restoreMessage = ref<{ kind: 'ok' | 'error'; text: string } | null>(null)

async function confirmRestore(): Promise<void> {
  const version = toRestore.value
  if (!version) {
    return
  }
  restoring.value = true
  try {
    await restoreVersion(version.id)
    restoreMessage.value = {
      kind: 'ok',
      text: `La versión ${version.id} vuelve a ser la publicada del curso ${version.academicYear}.`,
    }
    await Promise.all([versions.reload(), imports.reload()])
  } catch (cause) {
    restoreMessage.value = { kind: 'error', text: errorMessage(cause) }
  } finally {
    restoring.value = false
    toRestore.value = null
  }
}
</script>

<template>
  <section aria-labelledby="titulo">
    <p class="eyebrow">Administración</p>
    <h1 id="titulo" class="mt-1 text-3xl font-extrabold tracking-tight">Importaciones</h1>

    <section class="card mt-6 p-5" aria-labelledby="subir">
      <h2 id="subir" class="text-xl font-bold">Subir un dataset</h2>
      <p class="mt-1 text-sm text-muted">
        Un ZIP con los 9 ficheros CSV y el manifest.json de un curso. Se valida entero: si tiene un solo error no se
        guarda nada; si es válido queda en borrador hasta que lo publiques.
      </p>
      <form class="mt-4 grid gap-3" @submit.prevent="upload">
        <div>
          <label class="field-label" for="fichero">Fichero ZIP</label>
          <input
            id="fichero"
            ref="fileInput"
            class="field-control file:mr-3 file:rounded-md file:border-0 file:bg-brand-soft file:px-3 file:py-1.5 file:font-semibold file:text-brand-strong"
            type="file"
            accept=".zip,application/zip"
            aria-describedby="fichero-ayuda"
            @change="onFileChange"
          />
          <p id="fichero-ayuda" class="mt-1 text-sm text-muted">Tamaño máximo: 25 MB.</p>
        </div>
        <p v-if="uploadError" role="alert" class="rounded-md border border-danger bg-danger-soft p-3 text-danger">
          {{ uploadError }}
        </p>
        <div>
          <button type="submit" class="btn btn-primary" :disabled="uploading">
            {{ uploading ? 'Subiendo y validando…' : 'Subir y validar' }}
          </button>
        </div>
      </form>
    </section>

    <section class="mt-10" aria-labelledby="hechas">
      <h2 id="hechas" class="text-xl font-bold">Importaciones hechas</h2>
      <AsyncState :loading="imports.loading.value" :error="imports.error.value" @retry="imports.reload">
        <p v-if="!imports.data.value?.length" class="mt-3 text-muted">Todavía no se ha subido ningún dataset.</p>
        <div
          v-else
          class="card relative mt-3 overflow-x-auto"
          role="region"
          aria-label="Tabla desplazable en horizontal"
          tabindex="0"
        >
          <table class="data-table" data-testid="importaciones">
            <caption class="sr-only">
              Importaciones, de la más reciente a la más antigua
            </caption>
            <thead>
              <tr>
                <th scope="col">N.º</th>
                <th scope="col">Fecha</th>
                <th scope="col">Fichero</th>
                <th scope="col">Curso</th>
                <th scope="col">Resultado</th>
                <th scope="col">Versión</th>
                <th scope="col">Errores</th>
                <th scope="col">Avisos</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in imports.data.value" :key="item.id">
                <td class="tabular">
                  <RouterLink :to="{ name: 'admin-import', params: { id: item.id } }">
                    {{ item.id }}<span class="sr-only">: ver informe</span>
                  </RouterLink>
                </td>
                <td class="whitespace-nowrap">{{ formatDateTime(item.createdAt) }}</td>
                <td>{{ item.fileName ?? '—' }}</td>
                <td class="tabular">{{ item.academicYear ?? '—' }}</td>
                <td :class="item.outcome === 'RECHAZADA' ? 'font-semibold text-danger' : 'font-semibold'">
                  {{ importOutcomeLabel(item.outcome) }}
                </td>
                <td>{{ item.versionStatus ? versionStatusLabel(item.versionStatus) : '—' }}</td>
                <td class="tabular">{{ item.errorCount }}</td>
                <td class="tabular">{{ item.warningCount }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </AsyncState>
    </section>

    <section class="mt-10" aria-labelledby="versiones">
      <h2 id="versiones" class="text-xl font-bold">Versiones</h2>
      <p class="mt-1 text-sm text-muted">
        Solo hay una versión publicada por curso. Restaurar una versión archivada la vuelve a publicar y archiva la que
        esté publicada ahora.
      </p>
      <p
        v-if="restoreMessage"
        :role="restoreMessage.kind === 'error' ? 'alert' : 'status'"
        class="mt-3 rounded-md border p-3"
        :class="
          restoreMessage.kind === 'error' ? 'border-danger bg-danger-soft text-danger' : 'border-brand bg-brand-soft'
        "
        data-testid="mensaje-restaurar"
      >
        {{ restoreMessage.text }}
      </p>
      <AsyncState :loading="versions.loading.value" :error="versions.error.value" @retry="versions.reload">
        <div
          class="card relative mt-3 overflow-x-auto"
          role="region"
          aria-label="Tabla desplazable en horizontal"
          tabindex="0"
        >
          <table class="data-table" data-testid="versiones">
            <caption class="sr-only">
              Versiones de dataset, de la más reciente a la más antigua
            </caption>
            <thead>
              <tr>
                <th scope="col">Versión</th>
                <th scope="col">Curso</th>
                <th scope="col">Estado</th>
                <th scope="col">Publicada</th>
                <th scope="col">Archivada</th>
                <th scope="col"><span class="sr-only">Acciones</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="version in versions.data.value ?? []" :key="version.id" :data-version="version.id">
                <td class="tabular">{{ version.id }}</td>
                <td class="tabular">{{ version.academicYear }}</td>
                <td :class="{ 'font-semibold text-brand': version.status === 'PUBLICADO' }">
                  {{ versionStatusLabel(version.status) }}
                </td>
                <td class="whitespace-nowrap">{{ formatDateTime(version.publishedAt) }}</td>
                <td class="whitespace-nowrap">{{ formatDateTime(version.archivedAt) }}</td>
                <td>
                  <button
                    v-if="version.status === 'ARCHIVADO'"
                    type="button"
                    class="btn btn-secondary"
                    @click="toRestore = version"
                  >
                    Restaurar<span class="sr-only"> la versión {{ version.id }}</span>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </AsyncState>
    </section>

    <ConfirmDialog
      :open="toRestore !== null"
      title="¿Restaurar esta versión?"
      confirm-label="Sí, restaurar"
      :busy="restoring"
      @confirm="confirmRestore"
      @cancel="toRestore = null"
    >
      <p v-if="toRestore">
        La versión {{ toRestore.id }} volverá a ser la publicada del curso {{ toRestore.academicYear }} y la que está
        publicada ahora quedará archivada. El simulador empezará a usarla de inmediato.
      </p>
    </ConfirmDialog>
  </section>
</template>
