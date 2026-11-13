<script setup lang="ts">
import { useAppStore } from '@/stores/app'

const app = useAppStore()
</script>

<template>
  <div class="flex min-h-screen flex-col bg-slate-50 text-slate-900">
    <header class="border-b border-slate-200 bg-white">
      <div class="mx-auto max-w-3xl px-4 py-4">
        <p class="text-lg font-semibold">{{ app.appName }}</p>
      </div>
    </header>
    <main class="mx-auto w-full max-w-3xl flex-1 px-4 py-8">
      <RouterView />
    </main>
    <footer class="border-t border-slate-200 bg-white">
      <p class="mx-auto max-w-3xl px-4 py-4 text-sm text-slate-600">
        Resultado orientativo, no vinculante: la resolución final corresponde a la universidad y el
        reconocimiento de créditos conlleva tasas.
      </p>
    </footer>
  </div>
</template>
