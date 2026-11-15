<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { compare, getCycles, getDegrees } from '@/api/public'
import type { Comparison, Degree } from '@/api/types'
import AsyncState from '@/components/AsyncState.vue'
import OrientativeNotice from '@/components/OrientativeNotice.vue'
import { degreeLabel, formatEcts, formatNumber, formatPercentage } from '@/lib/format'
import { MAX_COMPARED_DEGREES, parseDegreeList, queryText } from '@/lib/urlState'
import { useAsyncData } from '@/lib/useAsyncData'

// Comparador: un ciclo frente a un máximo de 4 grados. El estado vive en la URL: ?ciclo=&grados=a,b
const route = useRoute()
const router = useRouter()

const cycleId = ref(queryText(route.query.ciclo))
const selected = ref<string[]>(parseDegreeList(route.query.grados))

// Si la URL cambia (atrás, adelante o un enlace), el formulario la sigue.
watch(
  () => route.query,
  (query) => {
    cycleId.value = queryText(query.ciclo)
    selected.value = parseDegreeList(query.grados)
  },
)

const catalog = useAsyncData(async () => {
  const [cycles, degrees] = await Promise.all([getCycles(), getDegrees()])
  return { cycles, degrees }
})

/** Grados agrupados por universidad, para elegirlos. */
const degreesByUniversity = computed(() => {
  const groups: { university: string; degrees: Degree[] }[] = []
  for (const degree of catalog.data.value?.degrees ?? []) {
    let group = groups.find((g) => g.university === degree.universityName)
    if (!group) {
      group = { university: degree.universityName, degrees: [] }
      groups.push(group)
    }
    group.degrees.push(degree)
  }
  return groups
})

const limitReached = computed(() => selected.value.length >= MAX_COMPARED_DEGREES)
const canCompare = computed(() => Boolean(cycleId.value) && selected.value.length > 0)

function submit(): void {
  if (canCompare.value) {
    void router.push({ name: 'compare', query: { ciclo: cycleId.value, grados: selected.value.join(',') } })
  }
}

const requested = computed(() => ({
  cycle: queryText(route.query.ciclo),
  degrees: parseDegreeList(route.query.grados),
}))
const comparison = useAsyncData<Comparison>(
  async () =>
    requested.value.cycle && requested.value.degrees.length
      ? compare(requested.value.cycle, requested.value.degrees)
      : null,
  requested,
)
</script>

<template>
  <section aria-labelledby="titulo">
    <p class="eyebrow">Comparador</p>
    <h1 id="titulo" class="mt-1 text-3xl font-extrabold tracking-tight">Compara grados lado a lado</h1>
    <p class="mt-3 max-w-2xl text-muted">
      Elige tu ciclo y hasta {{ MAX_COMPARED_DEGREES }} grados para ver cuántos ECTS te reconocería cada uno.
    </p>

    <AsyncState :loading="catalog.loading.value" :error="catalog.error.value" class="mt-6" @retry="catalog.reload">
      <form v-if="catalog.data.value" class="card mt-6 grid gap-5 p-5" @submit.prevent="submit">
        <div>
          <label class="field-label" for="ciclo">Ciclo de FP</label>
          <select id="ciclo" v-model="cycleId" class="field-control sm:max-w-xl">
            <option value="">Elige tu ciclo</option>
            <option v-for="cycle in catalog.data.value.cycles" :key="cycle.id" :value="cycle.id">
              {{ cycle.name }}<template v-if="cycle.regulationRef"> — {{ cycle.regulationRef }}</template>
            </option>
          </select>
        </div>

        <fieldset aria-describedby="grados-ayuda">
          <legend class="font-semibold">Grados que quieres comparar</legend>
          <p id="grados-ayuda" class="mt-1 text-sm text-muted" role="status">
            {{ selected.length }} de {{ MAX_COMPARED_DEGREES }} elegidos.
            <span v-if="limitReached">Para añadir otro, quita antes alguno.</span>
          </p>
          <div v-for="group in degreesByUniversity" :key="group.university" class="mt-3">
            <p class="eyebrow">{{ group.university }}</p>
            <ul class="mt-1 grid gap-1 sm:grid-cols-2">
              <li v-for="degree in group.degrees" :key="degree.id">
                <label class="flex min-h-11 cursor-pointer items-center gap-3 rounded-md px-2 hover:bg-brand-soft">
                  <input
                    v-model="selected"
                    type="checkbox"
                    :value="degree.id"
                    class="size-5 shrink-0 accent-brand"
                    :disabled="limitReached && !selected.includes(degree.id)"
                  />
                  <span>{{ degreeLabel(degree) }}</span>
                </label>
              </li>
            </ul>
          </div>
        </fieldset>

        <div>
          <button type="submit" class="btn btn-primary" :disabled="!canCompare">Comparar</button>
        </div>
      </form>
    </AsyncState>

    <AsyncState
      v-if="requested.cycle && requested.degrees.length"
      class="mt-8"
      :loading="comparison.loading.value"
      :error="comparison.error.value"
      loading-text="Comparando…"
      @retry="comparison.reload"
    >
      <section v-if="comparison.data.value" class="mt-8" aria-labelledby="resultados">
        <h2 id="resultados" class="text-xl font-bold">
          Desde {{ comparison.data.value.cycle.name }}
          <span class="font-normal text-muted">· curso {{ comparison.data.value.dataset.academicYear }}</span>
        </h2>
        <OrientativeNotice class="mt-4" />
        <ul
          class="mt-5 grid gap-4 sm:grid-cols-2"
          :class="{ 'lg:grid-cols-4': comparison.data.value.results.length > 2 }"
        >
          <li
            v-for="result in comparison.data.value.results"
            :key="result.degree.id"
            class="card flex flex-col p-4"
            data-testid="comparado"
          >
            <h3 class="font-bold">{{ degreeLabel(result.degree) }}</h3>
            <p class="text-sm text-muted">{{ result.degree.universityName }}</p>

            <template v-if="result.outcome === 'RECOGNIZED' && result.recognizedEcts !== null">
              <p class="tabular mt-4">
                <span class="text-5xl font-extrabold tracking-tight text-brand">
                  {{ formatNumber(result.recognizedEcts) }}
                </span>
                <span class="ml-1 font-semibold">ECTS</span>
              </p>
              <p class="text-sm">
                {{ formatPercentage(result.recognizedPercentage ?? 0) }} de {{ formatEcts(result.degreeTotalEcts) }}
              </p>
              <p v-if="result.capped" class="mt-2 rounded-md bg-cap-soft px-2 py-1 text-sm font-semibold text-cap">
                Limitado por el tope: las tablas suman {{ formatEcts(result.rulesTotalEcts) }}
              </p>
              <dl class="mt-3 grid grid-cols-[1fr_auto] gap-x-3 gap-y-1 text-sm">
                <dt class="text-muted">Asignaturas</dt>
                <dd class="tabular text-right font-semibold">{{ result.recognizedSubjects.length }}</dd>
                <dt class="text-muted">Créditos optativos</dt>
                <dd class="tabular text-right font-semibold">
                  {{ formatNumber(result.optativeCredits.reduce((sum, c) => sum + c.ects, 0)) }}
                </dd>
                <dt class="text-muted">Condicionados (no suman)</dt>
                <dd class="tabular text-right font-semibold">{{ result.conditionalRecognitions.length }}</dd>
                <dt class="text-muted">Por cursar</dt>
                <dd class="tabular text-right font-semibold">{{ formatEcts(result.pendingEcts) }}</dd>
              </dl>
            </template>
            <p v-else-if="result.outcome === 'NO_AGREEMENT'" class="mt-4 font-semibold">
              No existe tabla de reconocimiento firmada.
            </p>
            <p v-else class="mt-4 font-semibold">Todavía no tenemos datos de esta combinación.</p>

            <RouterLink
              class="mt-auto pt-4 font-semibold"
              :to="{ name: 'result', query: { ciclo: result.cycle.id, grado: result.degree.id } }"
            >
              Ver detalle<span class="sr-only"> de {{ degreeLabel(result.degree) }}</span>
            </RouterLink>
          </li>
        </ul>
      </section>
    </AsyncState>
  </section>
</template>
