<script setup lang="ts">
import { ref, useId } from 'vue'
import type { Citation } from '@/api/types'
import { documentTypeLabel, formatDate } from '@/lib/format'

// «Ver fuente»: el documento oficial, la página y el fragmento literal de los que sale un
// reconocimiento (regla inquebrantable 3: trazabilidad total).
defineProps<{
  source: Citation
  /** A qué se refiere, para que el botón tenga un nombre único: «Programación I». */
  subject: string
}>()

const open = ref(false)
const panelId = useId()
</script>

<template>
  <div>
    <button
      type="button"
      class="text-sm font-semibold text-brand underline underline-offset-4 hover:text-brand-strong"
      :aria-expanded="open"
      :aria-controls="panelId"
      @click="open = !open"
    >
      {{ open ? 'Ocultar fuente' : 'Ver fuente' }}
      <span class="sr-only">de {{ subject }}</span>
    </button>
    <div
      v-show="open"
      :id="panelId"
      class="mt-2 rounded-md border border-line bg-paper p-3 text-sm"
      data-testid="fuente"
    >
      <p class="font-semibold">
        <a v-if="source.url" :href="source.url" target="_blank" rel="noopener noreferrer">
          {{ source.title ?? 'Documento oficial' }}
          <span class="sr-only">(se abre en una pestaña nueva)</span>
        </a>
        <span v-else>{{ source.title ?? 'Documento sin título disponible' }}</span>
      </p>
      <p class="mt-1 text-muted">
        <span v-if="source.documentType">{{ documentTypeLabel(source.documentType) }} · </span>
        <span v-if="source.publicationDate">{{ formatDate(source.publicationDate) }} · </span>
        <span>Página {{ source.page }}</span>
      </p>
      <blockquote v-if="source.excerpt" class="mt-2 border-l-4 border-brand pl-3 italic">
        «{{ source.excerpt }}»
      </blockquote>
      <p v-else class="mt-2 text-muted">El documento no recoge un fragmento literal para esta regla.</p>
    </div>
  </div>
</template>
