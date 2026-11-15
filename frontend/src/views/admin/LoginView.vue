<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '@/api/client'
import { useAdminStore } from '@/stores/admin'
import { queryText, safeAdminRedirect } from '@/lib/urlState'

// Login de administrador. Tras entrar se vuelve a la pantalla en la que se estaba (?redirect=).
const route = useRoute()
const router = useRouter()
const admin = useAdminStore()

const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref<string | null>(null)

const sessionExpired = computed(() => queryText(route.query.caducada) === '1')

async function submit(): Promise<void> {
  busy.value = true
  error.value = null
  try {
    await admin.login(username.value, password.value)
    await router.replace(safeAdminRedirect(route.query.redirect))
  } catch (cause) {
    error.value = errorMessage(cause)
    password.value = ''
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section aria-labelledby="titulo" class="mx-auto max-w-md">
    <p class="eyebrow">Administración</p>
    <h1 id="titulo" class="mt-1 text-3xl font-extrabold tracking-tight">Iniciar sesión</h1>
    <p class="mt-3 text-muted">Solo para quienes mantienen los datos. Para simular no hace falta ninguna cuenta.</p>

    <p
      v-if="sessionExpired"
      class="mt-5 rounded-md border border-cap bg-cap-soft p-4"
      role="status"
      data-testid="caducada"
    >
      Tu sesión ha caducado. Vuelve a entrar y seguirás donde estabas.
    </p>

    <form class="card mt-6 grid gap-4 p-5" @submit.prevent="submit">
      <div>
        <label class="field-label" for="usuario">Usuario</label>
        <input
          id="usuario"
          v-model="username"
          class="field-control"
          type="text"
          name="username"
          autocomplete="username"
          autocapitalize="none"
          spellcheck="false"
          required
        />
      </div>
      <div>
        <label class="field-label" for="clave">Contraseña</label>
        <input
          id="clave"
          v-model="password"
          class="field-control"
          type="password"
          name="password"
          autocomplete="current-password"
          required
        />
      </div>
      <p v-if="error" role="alert" class="rounded-md border border-danger bg-danger-soft p-3 text-danger">
        {{ error }}
      </p>
      <button type="submit" class="btn btn-primary" :disabled="busy">{{ busy ? 'Entrando…' : 'Entrar' }}</button>
    </form>
  </section>
</template>
