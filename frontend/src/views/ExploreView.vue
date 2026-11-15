<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { explore, getCycles } from '@/api/public'
import type { Explore } from '@/api/types'
import AsyncState from '@/components/AsyncState.vue'
import OrientativeNotice from '@/components/OrientativeNotice.vue'
import { degreeLabel, formatEcts, formatNumber, formatPercentage } from '@/lib/format'
import { queryText } from '@/lib/urlState'
import { useAsyncData } from '@/lib/useAsyncData'

// Explorador: todos los grados que reconocen algo desde un ciclo, de más a menos ECTS.
// El estado vive en la URL: ?ciclo=
const route = useRoute()
const router = useRouter()

const cycles = useAsyncData(getCycles)
const cycleId = ref(queryText(route.query.ciclo))

watch(
  () => route.query.ciclo,
  (value) => (cycleId.value = queryText(value)),
)
watch(cycleId, (value) => {
  if (value !== queryText(route.query.ciclo)) {
    void router.push({ name: 'explore', query: value ? { ciclo: value } : {} })
  }
})

const requestedCycle = computed(() => queryText(route.query.ciclo))
const ranking = useAsyncData<Explore>(
  async () => (requestedCycle.value ? explore(requestedCycle.value) : null),
  requestedCycle,
)

const maxEcts = computed(() => Math.max(1, ...(ranking.data.value?.results.map((r) => r.recognizedEcts) ?? [])))
</script>

<template>
  <section aria-labelledby="titulo">
    <p class="eyebrow">Explorador</p>
    <h1 id="titulo" class="mt-1 text-3xl font-extrabold tracking-tight">¿Dónde te reconocen más?</h1>
    <p class="mt-3 max-w-2xl text-muted">
      Todos los grados que reconocen algo desde tu ciclo, ordenados de más a menos ECTS.
    </p>

    <AsyncState :loading="cycles.loading.value" :error="cycles.error.value" class="mt-6" @retry="cycles.reload">
      <div v-if="cycles.data.value" class="mt-6">
        <label class="field-label" for="ciclo">Ciclo de FP</label>
        <select id="ciclo" v-model="cycleId" class="field-control sm:max-w-xl">
          <option value="">Elige tu ciclo</option>
          <option v-for="cycle in cycles.data.value" :key="cycle.id" :value="cycle.id">
            {{ cycle.name }}<template v-if="cycle.regulationRef"> — {{ cycle.regulationRef }}</template>
          </option>
        </select>
      </div>
    </AsyncState>

    <AsyncState
      v-if="requestedCycle"
      class="mt-8"
      :loading="ranking.loading.value"
      :error="ranking.error.value"
      loading-text="Buscando grados…"
      @retry="ranking.reload"
    >
      <section v-if="ranking.data.value" class="mt-8" aria-labelledby="ranking">
        <h2 id="ranking" class="text-xl font-bold">
          {{ ranking.data.value.results.length }}
          {{ ranking.data.value.results.length === 1 ? 'grado reconoce' : 'grados reconocen' }} créditos
          <span class="font-normal text-muted">· curso {{ ranking.data.value.dataset.academicYear }}</span>
        </h2>
        <OrientativeNotice class="mt-4" />

        <p v-if="!ranking.data.value.results.length" class="card mt-5 p-5">
          No tenemos ningún grado con tabla de reconocimiento para este ciclo. Puede que exista y todavía no la hayamos
          recogido: consulta a la universidad que te interese.
        </p>

        <ol class="mt-5 grid gap-3">
          <li
            v-for="(entry, index) in ranking.data.value.results"
            :key="entry.degree.id"
            class="card grid gap-3 p-4 sm:grid-cols-[2rem_1fr_auto] sm:items-center"
            data-testid="explorado"
          >
            <span class="tabular text-lg font-bold text-muted" aria-hidden="true">{{ index + 1 }}</span>
            <div>
              <h3 class="font-bold">
                <RouterLink
                  :to="{ name: 'result', query: { ciclo: ranking.data.value.cycle.id, grado: entry.degree.id } }"
                >
                  {{ degreeLabel(entry.degree) }}
                </RouterLink>
              </h3>
              <p class="text-sm text-muted">{{ entry.degree.universityName }}</p>
              <div class="mt-2 h-2 overflow-hidden rounded-full bg-paper" aria-hidden="true">
                <div
                  class="h-full rounded-full"
                  :class="entry.capped ? 'bg-cap' : 'bg-brand'"
                  :style="{ width: `${(entry.recognizedEcts / maxEcts) * 100}%` }"
                ></div>
              </div>
              <p v-if="entry.capped" class="mt-1 text-sm font-semibold text-cap">
                Limitado por el tope: las tablas suman {{ formatEcts(entry.rulesTotalEcts) }}
              </p>
            </div>
            <p class="tabular sm:text-right">
              <span class="text-3xl font-extrabold text-brand">{{ formatNumber(entry.recognizedEcts) }}</span>
              <span class="ml-1 font-semibold">ECTS</span>
              <span class="block text-sm text-muted">{{ formatPercentage(entry.recognizedPercentage) }} del grado</span>
            </p>
          </li>
        </ol>
      </section>
    </AsyncState>
  </section>
</template>
