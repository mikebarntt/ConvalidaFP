import { createRouter, createWebHistory, type Router, type RouteRecordRaw } from 'vue-router'
import { useAdminStore } from '@/stores/admin'
import HomeView from '@/views/HomeView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    /** Título de la pestaña del navegador. */
    title: string
    /** La pantalla exige una sesión de administrador. */
    requiresAdmin?: boolean
  }
}

export const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: HomeView, meta: { title: 'Inicio' } },
  {
    path: '/resultado',
    name: 'result',
    component: () => import('@/views/ResultView.vue'),
    meta: { title: 'Resultado' },
  },
  {
    path: '/comparar',
    name: 'compare',
    component: () => import('@/views/CompareView.vue'),
    meta: { title: 'Comparador' },
  },
  {
    path: '/explorar',
    name: 'explore',
    component: () => import('@/views/ExploreView.vue'),
    meta: { title: 'Explorador' },
  },
  {
    path: '/fuentes',
    name: 'sources',
    component: () => import('@/views/SourcesView.vue'),
    meta: { title: 'Fuentes y metodología' },
  },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/views/admin/LoginView.vue'),
    meta: { title: 'Acceso de administración' },
  },
  {
    path: '/admin',
    redirect: { name: 'admin-imports' },
  },
  {
    path: '/admin/importaciones',
    name: 'admin-imports',
    component: () => import('@/views/admin/ImportsView.vue'),
    meta: { title: 'Importaciones', requiresAdmin: true },
  },
  {
    path: '/admin/importaciones/:id(\\d+)',
    name: 'admin-import',
    component: () => import('@/views/admin/ImportDetailView.vue'),
    meta: { title: 'Importación', requiresAdmin: true },
  },
  {
    path: '/admin/auditoria',
    name: 'admin-audit',
    component: () => import('@/views/admin/AuditView.vue'),
    meta: { title: 'Auditoría', requiresAdmin: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: 'Página no encontrada' },
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
})

/** Registra en un enrutador las reglas de navegación de la aplicación. */
export function installGuards(target: Router): void {
  // Las pantallas de administración exigen sesión. Si no la hay, se va al login recordando adónde
  // se quería ir.
  target.beforeEach(async (to) => {
    if (!to.meta.requiresAdmin) {
      return true
    }
    const admin = useAdminStore()
    if (!admin.checked) {
      await admin.check()
    }
    return admin.isLoggedIn ? true : { name: 'admin-login', query: { redirect: to.fullPath } }
  })

  target.afterEach((to, from) => {
    document.title = `${to.meta.title} · ConvalidaFP`
    // Al cambiar de pantalla, el foco pasa al contenido: quien navega con teclado o lector de
    // pantalla no se queda en el enlace que pulsó.
    if (from.name && to.path !== from.path) {
      requestAnimationFrame(() => document.getElementById('contenido')?.focus())
    }
  })
}

installGuards(router)
