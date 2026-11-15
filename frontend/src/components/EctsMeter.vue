<script setup lang="ts">
import { computed } from 'vue'
import { formatNumber } from '@/lib/format'

// Barra de ECTS: toda la barra es la carga del grado, la parte rellena es lo reconocido y una marca
// señala el tope aplicado.
const props = defineProps<{
  recognized: number
  total: number
  cap: number
  capped: boolean
}>()

const ratio = (value: number) => (props.total > 0 ? Math.min(100, Math.max(0, (value / props.total) * 100)) : 0)
const filled = computed(() => ratio(props.recognized))
const capPosition = computed(() => ratio(props.cap))
const description = computed(
  () =>
    `${formatNumber(props.recognized)} de ${formatNumber(props.total)} ECTS reconocidos. ` +
    `Tope aplicado: ${formatNumber(props.cap)} ECTS.`,
)
</script>

<template>
  <div role="img" :aria-label="description" data-testid="barra-ects">
    <div class="relative pt-7">
      <span
        class="tabular absolute top-0 -translate-x-1/2 whitespace-nowrap text-xs font-semibold text-cap"
        :style="{ left: `clamp(2.5rem, ${capPosition}%, calc(100% - 2.5rem))` }"
        aria-hidden="true"
      >
        Tope {{ formatNumber(cap) }}
      </span>
      <div class="relative h-4 overflow-hidden rounded-full border border-line bg-surface">
        <div
          class="h-full"
          :class="capped ? 'bg-cap' : 'bg-brand'"
          :style="{ width: `${filled}%` }"
          data-testid="barra-ects-relleno"
        ></div>
      </div>
      <span
        class="absolute top-5 h-8 w-0.5 bg-cap"
        :style="{ left: `${capPosition}%` }"
        aria-hidden="true"
        data-testid="barra-ects-tope"
      ></span>
    </div>
    <div class="tabular mt-2 flex justify-between text-xs text-muted" aria-hidden="true">
      <span>0</span>
      <span>{{ formatNumber(total) }} ECTS del grado</span>
    </div>
  </div>
</template>
