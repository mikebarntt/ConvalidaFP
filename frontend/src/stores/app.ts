import { defineStore } from 'pinia'

/** Estado global mínimo; los stores de simulación llegan en la Fase 6. */
export const useAppStore = defineStore('app', {
  state: () => ({ appName: 'ConvalidaFP' }),
})
