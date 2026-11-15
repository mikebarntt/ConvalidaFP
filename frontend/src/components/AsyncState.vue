<script setup lang="ts">
// Estados de carga y de error comunes a todas las pantallas. El contenido solo se muestra cuando
// ya hay datos.
defineProps<{
  loading: boolean
  error: string | null
  /** Texto que se anuncia mientras se carga, por ejemplo «Calculando el resultado…». */
  loadingText?: string
}>()

defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" role="status" class="flex items-center gap-3 py-8 text-muted">
    <span
      class="inline-block size-5 animate-spin rounded-full border-2 border-line border-t-brand"
      aria-hidden="true"
    ></span>
    <span>{{ loadingText ?? 'Cargando…' }}</span>
  </div>
  <div v-else-if="error" role="alert" class="rounded-md border border-danger bg-danger-soft p-4">
    <p class="font-semibold text-danger">No se ha podido cargar</p>
    <p class="mt-1">{{ error }}</p>
    <button type="button" class="btn btn-secondary mt-3" @click="$emit('retry')">Volver a intentarlo</button>
  </div>
  <slot v-else />
</template>
