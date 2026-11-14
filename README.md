# ConvalidaFP

Simulador de convalidaciones FP → Grado: dado un ciclo de FP de grado superior y un grado
universitario, calcula qué asignaturas se reconocen, cuántos ECTS suman y de qué documento oficial
sale cada reconocimiento.

> **Este código lo generó Fable 5.2 (Anthropic) a partir de la especificación del proyecto.**

> El resultado de una simulación es orientativo y no vinculante: la resolución final corresponde a
> la universidad y el reconocimiento de créditos conlleva tasas.

## Estado

**Fases 0 (contrato y esqueleto) y 1 (persistencia) cerradas.** Todavía no hay simulador: el
backend crea el esquema de la base de datos y responde a la comprobación de salud, y el frontend
muestra una página «en construcción». La base de datos arranca vacía: cargar datasets es la Fase 3.

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Contrato de datos, dataset de ejemplo, esqueleto, Docker Compose | Cerrada |
| 1 | Persistencia: esquema, entidades, repositorios y versionado de datasets | Cerrada |
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

Backend. Comprueba que el dataset de ejemplo cumple el contrato y cubre los 15 casos límite, y
prueba la persistencia contra una MariaDB real que los propios tests arrancan en Docker
(Testcontainers); por eso el comando les da acceso a Docker:

```bash
docker run --rm -v "${PWD}:/repo" -v convalidafp-m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /repo/backend maven:3.9-eclipse-temurin-21 mvn -B test
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

## Persistencia

El esquema lo crea Flyway al arrancar (`backend/src/main/resources/db/migration/`) y refleja
[CONTRACT.md](CONTRACT.md). Hibernate solo comprueba que las entidades encajan con él.

- **Versiones de dataset.** Cada curso se guarda como una versión (`dataset_version`) en estado
  `BORRADOR`, `PUBLICADO` o `ARCHIVADO`. La base de datos garantiza una sola versión publicada
  por curso. Al republicar un curso, la versión publicada anterior se archiva: no se borra.
- **Lecturas fijadas a una versión.** Todas las filas llevan `dataset_version_id` y todas las
  consultas de los repositorios lo reciben: una simulación resuelve la versión una vez y lee todo
  de ella, aunque se publique otra mientras tanto.
- **Identificadores.** `id` es la clave técnica; `code` es el identificador del contrato
  (`university_id`, `rule_id`…), único dentro de una versión y estable entre cursos.
- **Integridad entre versiones.** Las claves foráneas incluyen la versión: una fila no puede
  apuntar a datos de otra versión.
- **ECTS.** Siempre `DECIMAL(6,2)` en la base de datos y `BigDecimal` en Java. `NULL` significa
  «desconocido».

## Datos

- El formato de los datasets está en [CONTRACT.md](CONTRACT.md).
- El dataset de ejemplo y los casos límite que cubre están en
  [data/sample/README.md](data/sample/README.md).
- Todo lo que hay en `data/sample/` es ficticio. Ningún dato real se escribe sin documento fuente.

## Forma de trabajo

Una fase por sesión y **un commit por fase**, con el build y los tests en verde.
