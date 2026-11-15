import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { onUnauthorized } from './api/admin'
import App from './App.vue'
import { router } from './router'
import { useAdminStore } from './stores/admin'
import './style.css'

const app = createApp(App).use(createPinia()).use(router)

// Sesión caducada: cualquier petición de administración que reciba un 401 lleva al login,
// recordando la pantalla en la que se estaba para volver a ella después.
onUnauthorized(() => {
  useAdminStore().clear()
  const current = router.currentRoute.value
  if (current.name !== 'admin-login') {
    void router.replace({ name: 'admin-login', query: { redirect: current.fullPath, caducada: '1' } })
  }
})

app.mount('#app')
