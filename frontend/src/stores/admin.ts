import { defineStore } from 'pinia'
import * as adminApi from '@/api/admin'

/** Sesión del administrador. La sesión real vive en una cookie del servidor; aquí solo se refleja. */
export const useAdminStore = defineStore('admin', {
  state: () => ({
    username: null as string | null,
    /** Si ya se ha preguntado al servidor si hay sesión. */
    checked: false,
  }),
  getters: {
    isLoggedIn: (state) => state.username !== null,
  },
  actions: {
    /** Pregunta al servidor si hay una sesión iniciada (por ejemplo, al recargar la página). */
    async check(): Promise<void> {
      this.username = await adminApi.currentSession()
      this.checked = true
    },
    async login(username: string, password: string): Promise<void> {
      this.username = await adminApi.login(username, password)
      this.checked = true
    },
    async logout(): Promise<void> {
      try {
        await adminApi.logout()
      } finally {
        this.clear()
      }
    },
    /** La sesión ha terminado (cierre de sesión o caducidad). */
    clear(): void {
      this.username = null
      this.checked = true
    },
  },
})
