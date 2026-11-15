<template>
  <section aria-labelledby="titulo">
    <p class="eyebrow">Error 404</p>
    <h1 id="titulo" class="mt-1 text-3xl font-extrabold tracking-tight">Esta página no existe</h1>
    <p class="mt-3 text-muted">Puede que el enlace esté incompleto o que la página haya cambiado de sitio.</p>
    <RouterLink :to="{ name: 'home' }" class="btn btn-primary mt-5">Ir al inicio</RouterLink>
  </section>
</template>
