import { ref, shallowRef, watch, type Ref, type ShallowRef, type WatchSource } from 'vue'
import { errorMessage } from '@/api/client'

export interface AsyncData<T> {
  data: ShallowRef<T | null>
  loading: Ref<boolean>
  /** Mensaje en español listo para mostrar; null si no hay error. */
  error: Ref<string | null>
  reload: () => Promise<void>
}

/**
 * Carga datos de la API con sus estados de carga y de error.
 *
 * Si se pasa `source`, vuelve a cargar cada vez que cambia. El cargador puede devolver null para
 * indicar que todavía no hay nada que pedir. Si llegan dos respuestas desordenadas, gana la última
 * petición hecha.
 */
export function useAsyncData<T>(loader: () => Promise<T | null>, source?: WatchSource): AsyncData<T> {
  const data = shallowRef<T | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  let latest = 0

  async function reload(): Promise<void> {
    const current = ++latest
    loading.value = true
    error.value = null
    try {
      const result = await loader()
      if (current === latest) {
        data.value = result
      }
    } catch (cause) {
      if (current === latest) {
        data.value = null
        error.value = errorMessage(cause)
      }
    } finally {
      if (current === latest) {
        loading.value = false
      }
    }
  }

  if (source) {
    watch(source, reload, { immediate: true })
  } else {
    void reload()
  }
  return { data, loading, error, reload }
}
