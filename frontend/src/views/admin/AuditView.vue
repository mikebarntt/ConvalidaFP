<script setup lang="ts">
import { listAudit } from '@/api/admin'
import AsyncState from '@/components/AsyncState.vue'
import { auditActionLabel, formatDateTime } from '@/lib/format'
import { useAsyncData } from '@/lib/useAsyncData'

// Auditoría: quién hizo qué, sobre qué versión y cuándo. Solo lectura.
const audit = useAsyncData(() => listAudit())
</script>

<template>
  <section aria-labelledby="titulo">
    <p class="eyebrow">Administración</p>
    <h1 id="titulo" class="mt-1 text-3xl font-extrabold tracking-tight">Auditoría</h1>
    <p class="mt-3 max-w-2xl text-muted">
      Quién importó, publicó o restauró cada versión y cuándo, y los intentos de acceso fallidos. Este registro no se
      puede modificar ni borrar.
    </p>

    <AsyncState :loading="audit.loading.value" :error="audit.error.value" class="mt-6" @retry="audit.reload">
      <p v-if="!audit.data.value?.length" class="mt-6 text-muted">Todavía no hay nada registrado.</p>
      <div
        v-else
        class="card relative mt-6 overflow-x-auto"
        role="region"
        aria-label="Tabla desplazable en horizontal"
        tabindex="0"
      >
        <table class="data-table" data-testid="auditoria">
          <caption class="sr-only">
            Registro de auditoría, lo más reciente primero
          </caption>
          <thead>
            <tr>
              <th scope="col">Fecha</th>
              <th scope="col">Quién</th>
              <th scope="col">Acción</th>
              <th scope="col">Versión</th>
              <th scope="col">Curso</th>
              <th scope="col">Detalle</th>
              <th scope="col">IP</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="entry in audit.data.value" :key="entry.id" :data-action="entry.action">
              <td class="whitespace-nowrap">{{ formatDateTime(entry.occurredAt) }}</td>
              <td class="whitespace-nowrap">{{ entry.actor }}</td>
              <td class="font-semibold" :class="{ 'text-danger': entry.action === 'LOGIN_FALLIDO' }">
                {{ auditActionLabel(entry.action) }}
              </td>
              <td class="tabular">{{ entry.datasetVersionId ?? '—' }}</td>
              <td class="tabular">{{ entry.academicYear ?? '—' }}</td>
              <td>
                {{ entry.detail ?? '—' }}
                <RouterLink v-if="entry.importId" :to="{ name: 'admin-import', params: { id: entry.importId } }">
                  Importación {{ entry.importId }}
                </RouterLink>
              </td>
              <td class="tabular">{{ entry.ip ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </AsyncState>
  </section>
</template>
