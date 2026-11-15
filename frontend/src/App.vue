<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/admin'

const route = useRoute()
const router = useRouter()
const admin = useAdminStore()

const inAdmin = computed(() => route.path.startsWith('/admin'))

const publicLinks = [
  { name: 'home', label: 'Inicio' },
  { name: 'compare', label: 'Comparar' },
  { name: 'explore', label: 'Explorar' },
  { name: 'sources', label: 'Fuentes' },
]
const adminLinks = [
  { name: 'admin-imports', label: 'Importaciones' },
  { name: 'admin-audit', label: 'Auditoría' },
]
const links = computed(() => (inAdmin.value ? (admin.isLoggedIn ? adminLinks : []) : publicLinks))

async function logout(): Promise<void> {
  await admin.logout()
  await router.push({ name: 'admin-login' })
}
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <a
      href="#contenido"
      class="sr-only focus:not-sr-only focus:absolute focus:left-3 focus:top-3 focus:z-50 focus:rounded-md focus:bg-surface focus:px-4 focus:py-2 focus:font-semibold"
    >
      Saltar al contenido
    </a>

    <header class="border-b border-line bg-surface">
      <div class="mx-auto flex max-w-5xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
        <RouterLink
          :to="{ name: inAdmin ? 'admin-imports' : 'home' }"
          class="flex items-baseline gap-2 text-ink no-underline hover:text-ink"
        >
          <span class="text-xl font-extrabold tracking-tight">Convalida<span class="text-brand">FP</span></span>
          <span v-if="inAdmin" class="eyebrow">Administración</span>
        </RouterLink>

        <nav v-if="links.length" :aria-label="inAdmin ? 'Administración' : 'Principal'" class="flex-1">
          <ul class="-mx-2 flex flex-wrap items-center gap-x-1">
            <li v-for="link in links" :key="link.name">
              <RouterLink
                :to="{ name: link.name }"
                class="inline-flex min-h-11 items-center rounded-md px-2 font-semibold text-ink no-underline hover:bg-brand-soft hover:text-brand-strong"
                exact-active-class="text-brand underline decoration-2 underline-offset-8"
              >
                {{ link.label }}
              </RouterLink>
            </li>
          </ul>
        </nav>

        <div v-if="inAdmin && admin.isLoggedIn" class="flex items-center gap-3 text-sm">
          <span class="text-muted">
            <span class="sr-only">Sesión iniciada como</span>
            {{ admin.username }}
          </span>
          <button type="button" class="btn btn-secondary" @click="logout">Cerrar sesión</button>
        </div>
      </div>
    </header>

    <main id="contenido" tabindex="-1" class="mx-auto w-full max-w-5xl flex-1 px-4 py-8 focus:outline-none">
      <RouterView />
    </main>

    <footer class="border-t border-line bg-surface">
      <div class="mx-auto max-w-5xl px-4 py-6 text-sm text-muted">
        <p>
          Los resultados son orientativos, no vinculantes: la resolución final corresponde a la universidad y el
          reconocimiento de créditos conlleva tasas.
        </p>
        <p class="mt-3 flex flex-wrap gap-x-5 gap-y-1">
          <RouterLink :to="{ name: 'sources' }">Fuentes y metodología</RouterLink>
          <a href="/swagger-ui.html">API pública</a>
          <RouterLink :to="{ name: 'admin-imports' }">Administración</RouterLink>
        </p>
        <p class="mt-3">Sin cookies de seguimiento ni herramientas de analítica.</p>
      </div>
    </footer>
  </div>
</template>
