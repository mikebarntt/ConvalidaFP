# ConvalidaFP

Simulador de convalidaciones FP → Grado: dado un ciclo de FP de grado superior y un grado
universitario, calcula qué asignaturas se reconocen, cuántos ECTS suman y de qué documento oficial
sale cada reconocimiento.

> **Este código lo generó Fable 5.2 (Anthropic) a partir de la especificación del proyecto.**

> El resultado de una simulación es orientativo y no vinculante: la resolución final corresponde a
> la universidad y el reconocimiento de créditos conlleva tasas.

## Estado

**Fase 0 (contrato y esqueleto) cerrada.** Todavía no hay simulador: el backend solo responde a
la comprobación de salud y el frontend muestra una página «en construcción».

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Contrato de datos, dataset de ejemplo, esqueleto, Docker Compose | Cerrada |
| 1 | Persistencia | Pendiente |
| 2 | Motor de reglas | Pendiente |
| 3 | Importador | Pendiente |
| 4 | API pública | Pendiente |
| 5 | Administración y seguridad | Pendiente |
| 6 | Frontend | Pendiente |
| 7 | PDF, CI/CD y documentación | Pendiente |
| E | Extracción de datos reales | Pendiente |

## Arranque

Solo hace falta Docker Desktop (o Docker con Compose). Desde la carpeta del proyecto:

```bash
cp .env.example .env
```

```bash
docker compose up --build
```

- Frontend: <http://localhost:3000>
- Salud del backend: <http://localhost:8080/actuator/health> (debe responder `"status":"UP"`)

Para parar: `Ctrl+C` y después `docker compose down` (añade `-v` para borrar también la base de
datos).

Si los puertos 3000 u 8080 están ocupados, cámbialos en `.env`.

## Tests

No hace falta instalar Java ni Node: se ejecutan en contenedores.

Backend (incluye la comprobación de que el dataset de ejemplo cumple el contrato y cubre los 15
casos límite):

```bash
docker run --rm -v "${PWD}:/repo" -v convalidafp-m2:/root/.m2 -w /repo/backend maven:3.9-eclipse-temurin-21 mvn -B test
```

Frontend:

```bash
docker run --rm -v "${PWD}/frontend:/app" -w /app node:24-alpine sh -c "npm ci && npm test && npm run build"
```

## Estructura

```
backend/        Java 21 + Spring Boot (Maven). Paquete base es.convalidafp
  catalog/      universidades, ciclos, módulos, grados, asignaturas
  rules/        tablas, reglas y documentos fuente
  simulation/   motor de reglas (dominio puro)
  ingestion/    importador y validación de CSV
  admin/        login, importaciones, publicación, auditoría
frontend/       Vue 3 + TypeScript + Vite, Vue Router, Pinia, Tailwind CSS
data/sample/    dataset ficticio de ejemplo (dos cursos)
data/real/      datasets reales (vacío hasta la Fase E)
CONTRACT.md     contrato de datos: ficheros, columnas, semántica y validaciones
```

## Datos

- El formato de los datasets está en [CONTRACT.md](CONTRACT.md).
- El dataset de ejemplo y los casos límite que cubre están en
  [data/sample/README.md](data/sample/README.md).
- Todo lo que hay en `data/sample/` es ficticio. Ningún dato real se escribe sin documento fuente.

## Forma de trabajo

Una fase por sesión y **un commit por fase**, con el build y los tests en verde.
