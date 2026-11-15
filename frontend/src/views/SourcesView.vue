<script setup lang="ts">
import { getDataset, getSources } from '@/api/public'
import AsyncState from '@/components/AsyncState.vue'
import { documentTypeLabel, formatDate } from '@/lib/format'
import { useAsyncData } from '@/lib/useAsyncData'

// Fuentes y metodología: documentos oficiales usados, fecha de la última actualización y
// explicación del cálculo.
const page = useAsyncData(async () => {
  const [dataset, sources] = await Promise.all([getDataset(), getSources()])
  return { dataset, sources }
})
</script>

<template>
  <section aria-labelledby="titulo">
    <p class="eyebrow">Fuentes y metodología</p>
    <h1 id="titulo" class="mt-1 text-3xl font-extrabold tracking-tight">De dónde salen los datos</h1>
    <p class="mt-3 max-w-2xl text-muted">
      Todo lo que ves en el simulador sale de documentos oficiales. No añadimos ni completamos nada por nuestra cuenta:
      si un dato no aparece en el documento, lo dejamos sin rellenar.
    </p>

    <AsyncState :loading="page.loading.value" :error="page.error.value" class="mt-6" @retry="page.reload">
      <div v-if="page.data.value">
        <dl class="card mt-6 grid gap-x-8 gap-y-3 p-5 sm:grid-cols-3" data-testid="actualizacion">
          <div>
            <dt class="eyebrow">Curso vigente</dt>
            <dd class="tabular mt-1 text-lg font-bold">{{ page.data.value.dataset.academicYear }}</dd>
          </div>
          <div>
            <dt class="eyebrow">Última actualización</dt>
            <dd class="mt-1 text-lg font-bold">{{ formatDate(page.data.value.dataset.publishedAt) }}</dd>
          </div>
          <div>
            <dt class="eyebrow">Cursos disponibles</dt>
            <dd class="tabular mt-1 text-lg font-bold">{{ page.data.value.dataset.availableYears.join(', ') }}</dd>
          </div>
        </dl>

        <section class="mt-10" aria-labelledby="documentos">
          <h2 id="documentos" class="text-xl font-bold">Documentos oficiales usados</h2>
          <ul class="mt-4 grid gap-3">
            <li v-for="source in page.data.value.sources" :key="source.id" class="card p-4" data-testid="documento">
              <p class="eyebrow">{{ documentTypeLabel(source.documentType) }}</p>
              <p class="mt-1 font-semibold">
                <a v-if="source.url" :href="source.url" target="_blank" rel="noopener noreferrer">
                  {{ source.title }}
                  <span class="sr-only">(se abre en una pestaña nueva)</span>
                </a>
                <span v-else>{{ source.title }}</span>
              </p>
              <p class="mt-1 text-sm text-muted">
                <span v-if="source.issuer">{{ source.issuer }} · </span>
                <span v-if="source.publicationDate">Publicado el {{ formatDate(source.publicationDate) }}</span>
                <span v-else>Fecha de publicación no disponible</span>
                <span v-if="source.retrievedAt"> · Consultado el {{ formatDate(source.retrievedAt) }}</span>
              </p>
            </li>
          </ul>
        </section>
      </div>
    </AsyncState>

    <section class="mt-10 max-w-3xl" aria-labelledby="metodologia">
      <h2 id="metodologia" class="text-xl font-bold">Cómo se calcula el resultado</h2>
      <ol class="mt-4 grid gap-4">
        <li class="card p-4">
          <h3 class="font-bold">1. Buscamos la tabla de tu ciclo y tu grado</h3>
          <p class="mt-1">
            Si hay una tabla de reconocimiento firmada, la usamos. Si hemos comprobado que no existe, te lo decimos. Y
            si todavía no tenemos datos, también: son dos situaciones distintas.
          </p>
        </li>
        <li class="card p-4">
          <h3 class="font-bold">2. Aplicamos las reglas de la tabla</h3>
          <p class="mt-1">
            Cada regla dice qué módulos de tu ciclo hacen falta para reconocer una asignatura. Si una regla pide dos
            módulos, hacen falta los dos. Si dos reglas llevan a la misma asignatura, la asignatura cuenta una sola vez.
            Damos por hecho que tienes el título, es decir, todos los módulos superados.
          </p>
        </li>
        <li class="card p-4">
          <h3 class="font-bold">3. Sumamos los ECTS</h3>
          <p class="mt-1">
            Sumamos los ECTS de las asignaturas reconocidas y los créditos optativos. Los reconocimientos condicionados,
            como las prácticas externas, se muestran aparte y no se suman.
          </p>
        </li>
        <li class="card p-4">
          <h3 class="font-bold">4. Aplicamos el tope</h3>
          <p class="mt-1">
            El total reconocido no puede superar el 25 % de la carga del grado, ni el tope propio de la tabla si lo
            tiene. Si la suma lo supera, el total se limita y te lo avisamos. No descartamos asignaturas: es la
            universidad quien decide cuáles entran hasta el límite.
          </p>
        </li>
      </ol>
      <p class="mt-6 rounded-md border border-line bg-info-soft p-4">
        <strong>El resultado es orientativo, no vinculante.</strong> La resolución final corresponde a la universidad, y
        el reconocimiento de créditos conlleva tasas.
      </p>
    </section>
  </section>
</template>
