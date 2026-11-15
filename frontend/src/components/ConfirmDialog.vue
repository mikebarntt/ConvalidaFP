<script setup lang="ts">
import { ref, watch } from 'vue'

// Confirmación previa para las acciones que cambian lo publicado. Usa el elemento <dialog> nativo:
// el foco queda dentro mientras está abierto y Escape lo cierra.
const props = defineProps<{
  open: boolean
  title: string
  confirmLabel: string
  busy?: boolean
}>()

const emit = defineEmits<{ confirm: []; cancel: [] }>()

const dialog = ref<HTMLDialogElement | null>(null)

watch(
  () => props.open,
  (open) => {
    const element = dialog.value
    if (!element) {
      return
    }
    if (open && !element.open) {
      element.showModal()
    } else if (!open && element.open) {
      element.close()
    }
  },
  { flush: 'post' },
)
</script>

<template>
  <dialog
    ref="dialog"
    class="m-auto w-[min(32rem,calc(100vw-2rem))] rounded-lg border border-line bg-surface p-0 text-ink backdrop:bg-ink/50"
    aria-labelledby="confirm-dialog-title"
    @cancel.prevent="emit('cancel')"
  >
    <div class="p-5">
      <h2 id="confirm-dialog-title" class="text-lg font-bold">{{ title }}</h2>
      <div class="mt-2">
        <slot />
      </div>
      <div class="mt-5 flex flex-wrap justify-end gap-3">
        <button type="button" class="btn btn-secondary" :disabled="busy" @click="emit('cancel')">Cancelar</button>
        <button type="button" class="btn btn-primary" :disabled="busy" @click="emit('confirm')">
          {{ busy ? 'Un momento…' : confirmLabel }}
        </button>
      </div>
    </div>
  </dialog>
</template>
