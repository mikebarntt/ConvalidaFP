<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCycles, getDegrees, getUniversities } from '@/api/public'
import type { Degree } from '@/api/types'
import AsyncState from '@/components/AsyncState.vue'
import { degreeLabel, tableStatusLabel } from '@/lib/format'
import { queryText } from '@/lib/urlState'
import { useAsyncData } from '@/lib/useAsyncData'

// Inicio: elegir ciclo y después universidad y grado, o ver todos los grados.
const route = useRoute()
const router = useRouter()

const cycleId = ref(queryText(route.query.ciclo))
const universityId = ref('')
const degreeId = ref('')

const catalog = useAsyncData(async () => {
  const [cycles, universities] = await Promise.all([getCycles(), getUniversities()])
  return { cycles, universities }
})

// Qué grados tienen tabla (o convenio descartado) para el ciclo elegido.
const tables = useAsyncData(async () => (cycleId.value ? getDegrees({ cycle: cycleId.value }) : null), cycleId)
const degrees = useAsyncData(
  async () => (universityId.value ? getDegrees({ university: universityId.value }) : null),
  universityId,
)

watch(universityId, () => (degreeId.value = ''))

function statusOf(degree: Degree): string {
  const row = tables.data.value?.find((d) => d.id === degree.id)
  return tableStatusLabel(row?.tableStatus ?? null)
}

const canSubmit = computed(() => Boolean(cycleId.value && degreeId.value))

function submit(): void {
  if (canSubmit.value) {
    void router.push({ name: 'result', query: { ciclo: cycleId.value, grado: degreeId.value } })
  }
}
</script>

<template>
  <section aria-labelledby="titulo">
    <p class="eyebrow">Simulador de convalidaciones</p>
    <h1 id="titulo" class="mt-1 max-w-3xl text-3xl font-extrabold tracking-tight sm:text-4xl">
      De tu ciclo de FP al grado: qué asignaturas te reconocen
    </h1>
    <p class="mt-4 max-w-2xl text-lg text-muted">
      Elige tu ciclo de grado superior y un grado universitario. Te decimos qué asignaturas se reconocen, cuántos ECTS
      suman y de qué documento oficial sale cada una.
    </p>

    <AsyncState :loading="catalog.loading.value" :error="catalog.error.value" class="mt-8" @retry="catalog.reload">
      <form v-if="catalog.data.value" class="mt-8 grid gap-8" @submit.prevent="submit">
        <fieldset>
          <legend class="text-xl font-bold">
            <span class="tabular mr-2 text-brand">1.</span>
            ¿Qué ciclo has estudiado?
          </legend>
          <div class="mt-4 grid gap-3 sm:grid-cols-2">
            <label
              v-for="cycle in catalog.data.value.cycles"
              :key="cycle.id"
              class="card flex cursor-pointer items-start gap-3 p-4 has-checked:border-brand has-checked:bg-brand-soft has-checked:ring-2 has-checked:ring-brand"
            >
              <input v-model="cycleId" type="radio" name="ciclo" :value="cycle.id" class="mt-1 size-5 accent-brand" />
              <span>
                <span class="block font-semibold">{{ cycle.name }}</span>
                <span class="mt-1 block text-sm text-muted">
                  <span v-if="cycle.acronym">{{ cycle.acronym }}</span>
                  <span v-if="cycle.acronym && cycle.regulationRef"> · </span>
                  <span v-if="cycle.regulationRef">{{ cycle.regulationRef }}</span>
                </span>
              </span>
            </label>
          </div>
        </fieldset>

        <fieldset :disabled="!cycleId">
          <legend class="text-xl font-bold">
            <span class="tabular mr-2 text-brand">2.</span>
            ¿A qué grado quieres ir?
          </legend>
          <p v-if="!cycleId" class="mt-2 text-muted">Elige primero tu ciclo.</p>
          <div class="mt-4 grid gap-4 sm:grid-cols-2">
            <div>
              <label class="field-label" for="universidad">Universidad</label>
              <select id="universidad" v-model="universityId" class="field-control">
                <option value="">Elige una universidad</option>
                <option
                  v-for="university in catalog.data.value.universities"
                  :key="university.id"
                  :value="university.id"
                >
                  {{ university.name }}
                </option>
              </select>
            </div>
            <div>
              <label class="field-label" for="grado">Grado</label>
              <select
                id="grado"
                v-model="degreeId"
                class="field-control"
                :disabled="!universityId || degrees.loading.value"
                aria-describedby="grado-ayuda"
              >
                <option value="">{{ universityId ? 'Elige un grado' : 'Elige antes una universidad' }}</option>
                <option v-for="degree in degrees.data.value ?? []" :key="degree.id" :value="degree.id">
                  {{ degreeLabel(degree) }} — {{ statusOf(degree) }}
                </option>
              </select>
              <p id="grado-ayuda" class="mt-1 text-sm text-muted">
                Junto a cada grado indicamos si tiene tabla de reconocimiento para tu ciclo.
              </p>
              <p v-if="degrees.error.value" role="alert" class="mt-1 text-sm text-danger">{{ degrees.error.value }}</p>
            </div>
          </div>
        </fieldset>

        <div class="flex flex-wrap items-center gap-3">
          <button type="submit" class="btn btn-primary" :disabled="!canSubmit">Ver resultado</button>
          <RouterLink
            class="btn btn-secondary"
            :to="{ name: 'explore', query: cycleId ? { ciclo: cycleId } : {} }"
            data-testid="ver-todos"
          >
            Ver todos los grados
          </RouterLink>
        </div>
      </form>
    </AsyncState>

    <section class="mt-12 grid gap-4 sm:grid-cols-3" aria-label="Otras formas de consultar">
      <RouterLink :to="{ name: 'compare' }" class="card block p-4 text-ink no-underline hover:border-brand">
        <span class="block font-bold text-brand">Comparar grados</span>
        <span class="mt-1 block text-sm text-muted">Tu ciclo frente a un máximo de 4 grados, lado a lado.</span>
      </RouterLink>
      <RouterLink :to="{ name: 'explore' }" class="card block p-4 text-ink no-underline hover:border-brand">
        <span class="block font-bold text-brand">Explorar</span>
        <span class="mt-1 block text-sm text-muted">Todos los grados que reconocen algo, de más a menos ECTS.</span>
      </RouterLink>
      <RouterLink :to="{ name: 'sources' }" class="card block p-4 text-ink no-underline hover:border-brand">
        <span class="block font-bold text-brand">Fuentes y metodología</span>
        <span class="mt-1 block text-sm text-muted">De dónde salen los datos y cómo se calcula el resultado.</span>
      </RouterLink>
    </section>
  </section>
</template>
