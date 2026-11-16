// Comprueba que docker-compose.prod.yml cumple lo que exige el despliegue (README, «Despliegue»).
// Lee la configuración ya resuelta por Docker Compose:
//
//   docker compose -f docker-compose.prod.yml --env-file deploy/smoke-test.env config --format json \
//     | node deploy/check-prod-config.mjs
//
// Sale con código 1 y la lista de fallos si algo no se cumple.

import { readFileSync } from 'node:fs'

const chunks = []
for await (const chunk of process.stdin) {
  chunks.push(chunk)
}
const config = JSON.parse(Buffer.concat(chunks).toString('utf8'))
const services = config.services ?? {}
const failures = []
const check = (ok, message) => {
  if (!ok) {
    failures.push(message)
  }
}

const IP = { proxy: '172.30.0.10', frontend: '172.30.0.20', backend: '172.30.0.30' }

// 1. Solo el proxy HTTPS publica puertos, y solo el 80 y el 443. El 8080 nunca.
for (const [name, service] of Object.entries(services)) {
  const published = (service.ports ?? []).map((p) => String(p.published ?? ''))
  if (name === 'proxy') {
    check(published.includes('80') && published.includes('443'), 'el proxy debe publicar los puertos 80 y 443')
    check(published.every((p) => p === '80' || p === '443'), `el proxy publica puertos de más: ${published}`)
  } else {
    check(published.length === 0, `${name} no debe publicar puertos (publica ${published.join(', ')})`)
  }
  check(!published.includes('8080'), `${name} publica el puerto 8080`)
}

// 2. Sin datos de ejemplo: ni la variable que activa la carga ni la carpeta data/sample.
for (const [name, service] of Object.entries(services)) {
  const environment = service.environment ?? {}
  check(
    !('CONVALIDAFP_BOOTSTRAP_SAMPLE_DATASET_ENABLED' in environment),
    `${name} define CONVALIDAFP_BOOTSTRAP_SAMPLE_DATASET_ENABLED`,
  )
  for (const volume of service.volumes ?? []) {
    check(!String(volume.source ?? '').includes('sample'), `${name} monta ${volume.source}`)
  }
}

// 3. X-Forwarded-For solo de los proxies de confianza (Caddy y Nginx), con IP fija.
const backend = services.backend ?? {}
const proxies = backend.environment?.SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES
check(Boolean(proxies), 'el backend debe definir SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES')
if (proxies) {
  const trusted = new RegExp(`^(?:${proxies})$`)
  check(trusted.test(IP.proxy), `el proxy HTTPS (${IP.proxy}) debe ser de confianza`)
  check(trusted.test(IP.frontend), `Nginx (${IP.frontend}) debe ser de confianza`)
  for (const ip of [IP.backend, '172.30.0.1', '172.30.0.100', '10.0.0.1', '192.168.1.1', '127.0.0.1', '8.8.8.8']) {
    check(!trusted.test(ip), `${ip} no debe ser un proxy de confianza`)
  }
}
for (const name of ['proxy', 'frontend', 'backend']) {
  check(services[name]?.networks?.edge?.ipv4_address === IP[name], `${name} debe tener la IP fija ${IP[name]}`)
}

// 4. HTTPS: el proxy recibe el dominio y el Caddyfile.
const proxy = services.proxy ?? {}
check(Boolean(proxy.environment?.DOMAIN), 'el proxy necesita DOMAIN')
check(
  (proxy.volumes ?? []).some((v) => v.target === '/etc/caddy/Caddyfile'),
  'el proxy debe montar deploy/Caddyfile',
)

// El Caddyfile reenvía a la IP fija de Nginx.
const caddyfile = readFileSync(new URL('./Caddyfile', import.meta.url), 'utf8')
check(caddyfile.includes(`reverse_proxy ${IP.frontend}:80`), `deploy/Caddyfile debe reenviar a ${IP.frontend}:80`)

// 5. La base de datos solo está en la red interna.
const db = services.db ?? {}
check(Object.keys(db.networks ?? {}).join() === 'data', 'la base de datos solo debe estar en la red «data»')
check(config.networks?.data?.internal === true, 'la red «data» debe ser interna')

if (failures.length) {
  console.error('docker-compose.prod.yml NO cumple:\n- ' + failures.join('\n- '))
  process.exit(1)
}
console.log('docker-compose.prod.yml: configuración de producción correcta.')
