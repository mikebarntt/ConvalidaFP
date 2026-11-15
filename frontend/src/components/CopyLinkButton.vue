<script setup lang="ts">
import { ref } from 'vue'

// Copia la dirección de la pantalla actual. El estado vive en la URL, así que el enlace lleva al
// mismo resultado.
const status = ref<'idle' | 'copied' | 'failed'>('idle')

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(window.location.href)
    status.value = 'copied'
  } catch {
    status.value = 'failed'
  }
  window.setTimeout(() => (status.value = 'idle'), 4000)
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-3">
    <button type="button" class="btn btn-secondary" @click="copy">Copiar enlace</button>
    <span role="status" class="text-sm">
      <span v-if="status === 'copied'" class="font-semibold text-brand">Enlace copiado.</span>
      <span v-else-if="status === 'failed'">
        No se ha podido copiar. Copia la dirección desde la barra del navegador.
      </span>
    </span>
  </div>
</template>
