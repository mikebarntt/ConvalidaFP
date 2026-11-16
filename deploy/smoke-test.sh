#!/usr/bin/env bash
# Prueba de humo de docker-compose.prod.yml: arranca la configuración de producción en esta máquina
# (dominio «localhost», certificado de la CA local de Caddy), comprueba lo que exige el despliegue y
# lo borra todo al terminar. Necesita los puertos 80 y 443 libres. Se ejecuta en CI.
#
#   bash deploy/smoke-test.sh
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE=(docker compose -p convalidafp-smoke -f docker-compose.prod.yml --env-file deploy/smoke-test.env)
BASE=https://localhost
JAR=$(mktemp)
failures=0

cleanup() {
  rm -f "$JAR"
  "${COMPOSE[@]}" down -v --remove-orphans > /dev/null 2>&1 || true
}
trap cleanup EXIT

ok() { echo "  ok  $1"; }
ko() { echo "  FALLO  $1"; failures=$((failures + 1)); }
expect() { # descripción, valor obtenido, valor esperado
  if [[ "$2" == "$3" ]]; then ok "$1"; else ko "$1 (se obtuvo «$2», se esperaba «$3»)"; fi
}

echo "1. Configuración estática"
"${COMPOSE[@]}" config --format json | node deploy/check-prod-config.mjs

echo "2. Arranque (puede tardar unos minutos la primera vez)"
"${COMPOSE[@]}" up -d --build --wait --wait-timeout 600 > /dev/null

echo "3. Comprobaciones"
expect "HTTP redirige a HTTPS" \
  "$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' http://localhost/resultado)" "308 https://localhost/resultado"

expect "la SPA se sirve por HTTPS" "$(curl -sk -o /dev/null -w '%{http_code}' $BASE/)" "200"

expect "cabecera HSTS" \
  "$(curl -skI $BASE/ | tr -d '\r' | grep -ci '^strict-transport-security: max-age=31536000')" "1"

# Sin datos de ejemplo: no hay ningún dataset publicado.
expect "no se carga el dataset de ejemplo" "$(curl -sk -o /dev/null -w '%{http_code}' $BASE/api/v1/dataset)" "404"

# El backend no es accesible directamente: Docker no publica su puerto en el anfitrión.
backend_id=$("${COMPOSE[@]}" ps -q backend)
bindings=$(docker inspect -f '{{json .HostConfig.PortBindings}}' "$backend_id")
if [[ "$bindings" == "{}" || "$bindings" == "null" ]]; then
  ok "el backend no publica ningún puerto (tampoco el 8080)"
else
  ko "el backend no debe publicar puertos (publica $bindings)"
fi

# X-Forwarded-For falsificado: cambiarlo en cada petición no salta el límite de 60 por minuto.
limited=0
for i in $(seq 1 70); do
  code=$(curl -sk -o /dev/null -w '%{http_code}' -H "X-Forwarded-For: 198.51.100.$i" $BASE/api/v1/cycles || true)
  if [[ "$code" == "429" ]]; then
    limited=$((limited + 1))
  fi
done
if ((limited > 0)); then
  ok "X-Forwarded-For del cliente se ignora ($limited de 70 peticiones limitadas)"
else
  ko "X-Forwarded-For del cliente salta el límite de peticiones"
fi

# Cookies seguras: el login por HTTPS crea una sesión con la cookie marcada como Secure.
curl -sk -c "$JAR" -b "$JAR" -o /dev/null $BASE/api/v1/admin/csrf
token=$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR")
headers=$(curl -sk -c "$JAR" -b "$JAR" -D - -o /dev/null -H "X-XSRF-TOKEN: $token" \
  --data-urlencode "username=admin-humo" --data-urlencode "password=solo-para-la-prueba-de-humo-0123" \
  $BASE/api/v1/admin/login | tr -d '\r')
expect "login del administrador por HTTPS" "$(echo "$headers" | head -1 | awk '{print $2}')" "200"
expect "cookie de sesión Secure" "$(echo "$headers" | grep -i '^set-cookie: JSESSIONID=' | grep -ci 'secure')" "1"

if ((failures > 0)); then
  echo "Prueba de humo: $failures fallos"
  "${COMPOSE[@]}" logs --tail 50 backend proxy || true
  exit 1
fi
echo "Prueba de humo superada."
