<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { reportUrl, simulate } from '@/api/public'
import type { RecognizedSubject, Simulation } from '@/api/types'
import AsyncState from '@/components/AsyncState.vue'
import CopyLinkButton from '@/components/CopyLinkButton.vue'
import EctsMeter from '@/components/EctsMeter.vue'
import OrientativeNotice from '@/components/OrientativeNotice.vue'
import SourceDisclosure from '@/components/SourceDisclosure.vue'
import {
  degreeLabel,
  documentTypeLabel,
  formatCourseYear,
  formatDate,
  formatEcts,
  formatNumber,
  formatPercentage,
  subjectTypeLabel,
} from '@/lib/format'
import { queryText } from '@/lib/urlState'
import { useAsyncData } from '@/lib/useAsyncData'

// Resultado de una simulación. El estado vive en la URL: ?ciclo=&grado=&curso=
const route = useRoute()
const cycleId = computed(() => queryText(route.query.ciclo))
const degreeId = computed(() => queryText(route.query.grado))
const year = computed(() => queryText(route.query.curso))
const hasSelection = computed(() => Boolean(cycleId.value && degreeId.value))

const simulation = useAsyncData<Simulation>(
  async () => (hasSelection.value ? simulate(cycleId.value, degreeId.value, year.value || undefined) : null),
  () => [cycleId.value, degreeId.value, year.value],
)
const result = computed(() => simulation.data.value)
const pdfUrl = computed(() => reportUrl(cycleId.value, degreeId.value, year.value || undefined))

/** Asignaturas reconocidas agrupadas por curso, con los cursos en orden. */
const subjectsByYear = computed(() => {
  const groups: { courseYear: number | null; subjects: RecognizedSubject[] }[] = []
  for (const subject of result.value?.recognizedSubjects ?? []) {
    let group = groups.find((g) => g.courseYear === subject.courseYear)
    if (!group) {
      group = { courseYear: subject.courseYear, subjects: [] }
      groups.push(group)
    }
    group.subjects.push(subject)
  }
  return groups
})

/** De dónde sale el tope aplicado: del 25 % legal o del tope propio de la tabla. */
const capReason = computed(() => {
  const r = result.value
  if (!r || r.appliedCapEcts === null) {
    return ''
  }
  return r.tableCapEcts !== null && r.legalCapEcts !== null && r.tableCapEcts < r.legalCapEcts
    ? 'el tope propio de esta tabla de reconocimiento'
    : 'el 25 % de la carga del grado'
})

const remainingCount = computed(
  () => result.value?.remainingByYear.reduce((count, group) => count + group.subjects.length, 0) ?? 0,
)
</script>

<template>
  <section aria-labelledby="titulo">
    <p class="eyebrow">Resultado</p>

    <div v-if="!hasSelection">
      <h1 id="titulo" class="mt-1 text-3xl font-extrabold tracking-tight">Falta elegir ciclo y grado</h1>
      <p class="mt-3 text-muted">
        Para ver un resultado necesitamos saber de qué ciclo vienes y a qué grado quieres ir.
      </p>
      <RouterLink :to="{ name: 'home' }" class="btn btn-primary mt-5">Elegir ciclo y grado</RouterLink>
    </div>

    <AsyncState
      v-else
      :loading="simulation.loading.value"
      :error="simulation.error.value"
      loading-text="Calculando el resultado…"
      @retry="simulation.reload"
    >
      <div v-if="result" :data-outcome="result.outcome">
        <h1 id="titulo" class="mt-1 text-2xl font-extrabold tracking-tight sm:text-3xl">
          {{ degreeLabel(result.degree) }}
        </h1>
        <p class="mt-2 text-muted">
          {{ result.degree.universityName }} · desde
          <strong class="text-ink">{{ result.cycle.name }}</strong>
          <span v-if="result.cycle.regulationRef"> ({{ result.cycle.regulationRef }})</span>
          · curso {{ result.dataset.academicYear }}
        </p>

        <OrientativeNotice class="mt-5" />

        <!-- Reconocimiento calculado -->
        <template v-if="result.outcome === 'RECOGNIZED' && result.recognizedEcts !== null">
          <div class="card mt-6 p-5 sm:p-6" data-testid="resumen">
            <p class="tabular flex flex-wrap items-baseline gap-x-3">
              <span
                class="text-6xl font-extrabold tracking-tight text-brand sm:text-7xl"
                data-testid="ects-reconocidos"
              >
                {{ formatNumber(result.recognizedEcts) }}
              </span>
              <span class="text-xl font-semibold">
                de {{ formatNumber(result.degreeTotalEcts ?? 0) }} ECTS
                {{ result.capped ? 'reconocibles' : 'reconocidos' }}
              </span>
            </p>
            <p class="mt-1 text-lg" data-testid="porcentaje">
              <strong>{{ formatPercentage(result.recognizedPercentage ?? 0) }}</strong> del grado. Te quedarían
              <strong>{{ formatEcts(result.pendingEcts) }}</strong> por cursar.
            </p>
            <EctsMeter
              class="mt-4"
              :recognized="result.recognizedEcts"
              :total="result.degreeTotalEcts ?? 0"
              :cap="result.appliedCapEcts ?? 0"
              :capped="result.capped === true"
            />
            <p v-if="!result.capped" class="mt-3 text-sm text-muted">
              El máximo reconocible en este grado es {{ formatEcts(result.appliedCapEcts) }} ({{ capReason }}).
            </p>
          </div>

          <div
            v-if="result.capped"
            class="mt-4 rounded-md border border-cap bg-cap-soft p-4"
            role="note"
            data-testid="aviso-tope"
          >
            <p class="font-bold text-cap">El resultado está limitado por el tope</p>
            <p class="mt-1">
              Las tablas suman {{ formatEcts(result.rulesTotalEcts) }}, pero el máximo reconocible es
              {{ formatEcts(result.appliedCapEcts) }} ({{ capReason }}). La universidad decide qué asignaturas entran
              hasta ese límite.
            </p>
          </div>

          <section class="mt-8" aria-labelledby="reconocidas">
            <h2 id="reconocidas" class="text-xl font-bold">
              {{
                result.capped
                  ? `Reconocibles hasta el tope de ${formatEcts(result.appliedCapEcts)}`
                  : 'Asignaturas reconocidas'
              }}
            </h2>
            <p v-if="!result.recognizedSubjects.length" class="mt-2 text-muted">
              Con tu ciclo no se reconoce ninguna asignatura concreta de este grado.
            </p>
            <div v-for="group in subjectsByYear" :key="String(group.courseYear)" class="mt-5">
              <h3 class="eyebrow">{{ formatCourseYear(group.courseYear) }}</h3>
              <ul class="mt-2 grid gap-2">
                <li
                  v-for="subject in group.subjects"
                  :key="subject.subjectId"
                  class="card p-4"
                  data-testid="reconocida"
                >
                  <div class="flex items-baseline justify-between gap-3">
                    <span class="font-semibold">{{ subject.name }}</span>
                    <span class="tabular shrink-0 font-semibold">{{ formatEcts(subject.ects) }}</span>
                  </div>
                  <SourceDisclosure class="mt-2" :source="subject.source" :subject="subject.name" />
                </li>
              </ul>
            </div>
          </section>

          <section v-if="result.optativeCredits.length" class="mt-8" aria-labelledby="optativos">
            <h2 id="optativos" class="text-xl font-bold">Créditos optativos</h2>
            <p class="mt-1 text-sm text-muted">
              Se reconocen como créditos optativos, sin corresponder a una asignatura concreta.
            </p>
            <ul class="mt-3 grid gap-2">
              <li
                v-for="credits in result.optativeCredits"
                :key="credits.ruleId"
                class="card p-4"
                data-testid="optativos"
              >
                <div class="flex items-baseline justify-between gap-3">
                  <span class="font-semibold">Créditos optativos</span>
                  <span class="tabular shrink-0 font-semibold">{{ formatEcts(credits.ects) }}</span>
                </div>
                <SourceDisclosure class="mt-2" :source="credits.source" subject="los créditos optativos" />
              </li>
            </ul>
          </section>

          <section v-if="result.conditionalRecognitions.length" class="mt-8" aria-labelledby="condicionados">
            <h2 id="condicionados" class="text-xl font-bold">Reconocimientos condicionados</h2>
            <p class="mt-1 text-sm text-muted">
              Dependen de una condición que valora la universidad. <strong>No están sumados</strong> en el total.
            </p>
            <ul class="mt-3 grid gap-2">
              <li
                v-for="conditional in result.conditionalRecognitions"
                :key="conditional.ruleId"
                class="card border-dashed p-4"
                data-testid="condicionado"
              >
                <div class="flex items-baseline justify-between gap-3">
                  <span class="font-semibold">{{ conditional.subjectName ?? 'Créditos optativos' }}</span>
                  <span class="tabular shrink-0 font-semibold">{{ formatEcts(conditional.ects) }}</span>
                </div>
                <p class="mt-1 text-sm">
                  <span class="font-semibold">Condición:</span>
                  {{ conditional.conditionText ?? 'condición no especificada en el documento' }}
                </p>
                <SourceDisclosure
                  class="mt-2"
                  :source="conditional.source"
                  :subject="conditional.subjectName ?? 'los créditos optativos condicionados'"
                />
              </li>
            </ul>
          </section>

          <section class="mt-8" aria-labelledby="restantes">
            <h2 id="restantes" class="text-xl font-bold">Asignaturas restantes</h2>
            <p class="mt-1 text-sm text-muted">
              {{ remainingCount }} asignaturas del grado que no aparecen en la tabla de reconocimiento.
              <span v-if="result.capped">
                Como el resultado está limitado, además tendrás que cursar parte de las asignaturas de la lista
                anterior.
              </span>
            </p>
            <details
              v-for="group in result.remainingByYear"
              :key="String(group.courseYear)"
              class="card mt-2"
              data-testid="restantes-curso"
            >
              <summary class="flex min-h-11 items-center justify-between gap-3 px-4 py-2 font-semibold">
                <span>{{ formatCourseYear(group.courseYear) }}</span>
                <span class="tabular text-sm font-normal text-muted">{{ group.subjects.length }} asignaturas</span>
              </summary>
              <ul class="border-t border-line px-4 py-2">
                <li
                  v-for="subject in group.subjects"
                  :key="subject.id"
                  class="flex items-baseline justify-between gap-3 border-b border-line py-2 last:border-b-0"
                >
                  <span>
                    {{ subject.name }}
                    <span class="text-sm text-muted">· {{ subjectTypeLabel(subject.subjectType) }}</span>
                  </span>
                  <span class="tabular shrink-0 text-sm">{{ formatEcts(subject.ects) }}</span>
                </li>
              </ul>
            </details>
          </section>
        </template>

        <!-- Verificado que no hay convenio -->
        <div v-else-if="result.outcome === 'NO_AGREEMENT'" class="card mt-6 p-5 sm:p-6" data-testid="sin-convenio">
          <h2 class="text-2xl font-extrabold">No existe tabla de reconocimiento firmada</h2>
          <p class="mt-3">
            Lo hemos comprobado: para pasar de este ciclo a este grado
            <strong>no hay ninguna tabla de reconocimiento firmada</strong>. Eso significa que no hay asignaturas
            reconocidas de forma automática.
          </p>
          <p class="mt-3">Aun así, puedes preguntar en la universidad: a veces estudian las solicitudes una a una.</p>
          <p v-if="result.noAgreement?.notes" class="mt-3 text-sm text-muted">Nota: {{ result.noAgreement.notes }}</p>
          <p v-if="result.noAgreement?.source" class="mt-3 text-sm">
            <span class="mr-1 font-semibold">Fuente:</span>
            <a
              v-if="result.noAgreement.source.url"
              :href="result.noAgreement.source.url"
              target="_blank"
              rel="noopener noreferrer"
            >
              {{ result.noAgreement.source.title }}
            </a>
            <span v-else>{{ result.noAgreement.source.title }}</span>
            <span class="text-muted">
              · {{ documentTypeLabel(result.noAgreement.source.documentType) }}
              <span v-if="result.noAgreement.source.publicationDate">
                · {{ formatDate(result.noAgreement.source.publicationDate) }}
              </span>
            </span>
          </p>
        </div>

        <!-- Sin datos -->
        <div v-else class="card mt-6 p-5 sm:p-6" data-testid="sin-datos">
          <h2 class="text-2xl font-extrabold">Todavía no tenemos datos de esta combinación</h2>
          <p class="mt-3">
            No hemos encontrado ningún documento oficial que relacione este ciclo con este grado.
            <strong>No significa que no haya convenio</strong>: significa que todavía no lo hemos podido comprobar.
          </p>
          <p class="mt-3">Consulta directamente a la universidad para saber si reconoce créditos de tu ciclo.</p>
        </div>

        <div class="mt-8 flex flex-wrap items-center gap-3">
          <a class="btn btn-primary" :href="pdfUrl" download data-testid="descargar-pdf">Descargar informe PDF</a>
          <CopyLinkButton />
          <RouterLink class="btn btn-secondary" :to="{ name: 'explore', query: { ciclo: result.cycle.id } }">
            Ver otros grados
          </RouterLink>
          <RouterLink class="btn btn-secondary" :to="{ name: 'home', query: { ciclo: result.cycle.id } }">
            Cambiar de grado
          </RouterLink>
        </div>

        <p class="mt-6 text-sm text-muted">
          Datos del curso {{ result.dataset.academicYear }}, actualizados el
          {{ formatDate(result.dataset.publishedAt) }}.
          <RouterLink :to="{ name: 'sources' }">Fuentes y metodología</RouterLink>
        </p>
      </div>
    </AsyncState>
  </section>
</template>
