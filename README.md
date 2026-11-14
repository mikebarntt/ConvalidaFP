# ConvalidaFP

Simulador de convalidaciones FP → Grado: dado un ciclo de FP de grado superior y un grado
universitario, calcula qué asignaturas se reconocen, cuántos ECTS suman y de qué documento oficial
sale cada reconocimiento.

> **Este código lo generó Fable 5.2 (Anthropic) a partir de la especificación del proyecto.**

> El resultado de una simulación es orientativo y no vinculante: la resolución final corresponde a
> la universidad y el reconocimiento de créditos conlleva tasas.

## Estado

**Fases 0 a 4 cerradas**: contrato y esqueleto, persistencia, motor de reglas, importador y API
pública. Al arrancar con Docker Compose, el backend crea el esquema, carga y publica el dataset de
ejemplo y ya se puede simular por la API. El frontend sigue mostrando una página «en construcción»
(Fase 6) y todavía no hay panel de administración (Fase 5) ni informe PDF (Fase 7).

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Contrato de datos, dataset de ejemplo, esqueleto, Docker Compose | Cerrada |
| 1 | Persistencia: esquema, entidades, repositorios y versionado de datasets | Cerrada |
| 2 | Motor de reglas: dominio puro, 15 casos límite, cobertura ≥ 90 % | Cerrada |
| 3 | Importador: validación, borrador, diferencias, publicación versionada | Cerrada |
| 4 | API pública: endpoints, OpenAPI, caché, límite de peticiones, Problem Details | Cerrada |
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
- Una simulación: <http://localhost:3000/api/v1/simulations?cycle=CF-EJ&degree=UEJ-GP>
- Documentación de la API: <http://localhost:8080/swagger-ui.html>

Para parar: `Ctrl+C` y después `docker compose down` (añade `-v` para borrar también la base de
datos).

Si los puertos 3000 u 8080 están ocupados, cámbialos en `.env`.

## Tests

No hace falta instalar Java ni Node: se ejecutan en contenedores.

Backend. Comprueba que el dataset de ejemplo cumple el contrato y cubre los 15 casos límite, y
prueba la persistencia, el importador y la API contra una MariaDB real que los propios tests
arrancan en Docker (Testcontainers, un único contenedor); por eso el comando les da acceso a Docker:

```bash
docker run --rm -v "${PWD}:/repo" -v convalidafp-m2:/root/.m2 -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /repo/backend maven:3.9-eclipse-temurin-21 mvn -B verify
```

`verify` ejecuta todos los tests y además falla si la cobertura de líneas del motor de reglas baja
del 90 %. El informe queda en `backend/target/site/jacoco/index.html`.

Frontend:

```bash
docker run --rm -v "${PWD}/frontend:/app" -w /app node:24-alpine sh -c "npm ci && npm test && npm run build"
```

## Estructura

```
backend/        Java 21 + Spring Boot (Maven). Paquete base es.convalidafp
  api/          endpoints públicos, caché y adaptador de las entidades al motor
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

## Motor de reglas

Está en el paquete `simulation` y es dominio puro: no depende de Spring ni de JPA, no lee de la
base de datos y no contiene ningún caso particular de universidad. Un test lo vigila.

```
SimulationEngine.simulate(input, table, rules, degree) → SimulationResult
```

- **Tres resultados**, los tres válidos: `Recognized` (reconocimiento calculado), `NoAgreement`
  (verificado que no hay tabla firmada) y `NoData` (sin datos para esa combinación).
- **Reglas como datos.** Todos los módulos de una regla son obligatorios; dos reglas con la misma
  asignatura son alternativas y la asignatura cuenta una vez, con la regla de menor identificador
  como fuente. Los créditos optativos se suman. Las reglas condicionadas van aparte y no suman.
- **Tope.** `min(suma, 25 % del grado, tope de la tabla)`. El 25 % se lee de la configuración
  (`convalidafp.simulation.max-recognition-ratio`). Si la suma supera el tope, el total se limita
  y se marca `capped`; el motor no descarta ninguna asignatura.
- **Módulos del estudiante.** La v1 asume que tiene el título completo, pero el motor acepta un
  conjunto concreto de módulos.
- **Aritmética** en `BigDecimal`, sin redondeos. La misma entrada da siempre el mismo resultado.

## API pública

Solo lectura, en `/api/v1`, para la SPA y para terceros. Está en el paquete `api`.

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/v1/dataset` | Versión publicada, curso y cursos disponibles |
| GET | `/api/v1/cycles` | Ciclos |
| GET | `/api/v1/universities` | Universidades |
| GET | `/api/v1/degrees?university=&cycle=` | Grados; con `cycle`, solo los que tienen fila para ese ciclo |
| GET | `/api/v1/degrees/{id}/subjects` | Asignaturas de un grado |
| GET | `/api/v1/simulations?cycle=&degree=` | Simulación |
| GET | `/api/v1/comparisons?cycle=&degrees=a,b,c` | Comparación de hasta 4 grados |
| GET | `/api/v1/explore?cycle=` | Grados que reconocen algo, de más a menos ECTS |
| GET | `/api/v1/sources/{id}` | Documento oficial de una regla |

Todos aceptan `year=AAAA-AA`; sin él se usa el curso vigente publicado.

- **Tres resultados de simulación**, los tres con código 200: `RECOGNIZED`, `NO_AGREEMENT` y
  `NO_DATA`. Todos llevan el aviso de que el resultado es orientativo.
- **Una sola versión por petición.** Cada petición obtiene la versión publicada entera, como objeto
  inmutable en memoria, y calcula todo con ella aunque se publique otra mientras tanto.
- **Caché.** Las versiones publicadas se guardan en memoria (Caffeine). Publicar o restaurar una
  versión lanza un evento que vacía la caché una vez confirmada la transacción.
- **Caché HTTP.** `ETag` ligado a la versión publicada y `Cache-Control: max-age=300, public`;
  con `If-None-Match` se responde 304.
- **Límite de peticiones.** 60 por minuto por IP (Bucket4j); al superarlo, 429 con `Retry-After`.
- **Errores** en Problem Details (RFC 9457), con mensajes en español.
- **CORS** cerrado por defecto: la SPA y la API se sirven desde el mismo dominio. Otros orígenes se
  permiten con `convalidafp.api.allowed-origins`.
- **OpenAPI** en `/v3/api-docs` y Swagger UI en `/swagger-ui.html` (puerto del backend).
- **Métrica** `convalidafp.simulation` con el tiempo de cada simulación.

## Importador

Está en el paquete `ingestion`. Todavía no tiene pantalla ni endpoint (Fase 5): se usa desde el
código y desde el arranque local.

- **Entrada.** Una carpeta de curso o un ZIP con los 9 CSV y el `manifest.json`.
- **Validación en dos niveles.** Un solo **error** rechaza la carga entera y no se guarda nada. Los
  **avisos** no bloquean. Cada incidencia lleva el código de CONTRACT.md, fichero, fila, columna y
  motivo.
- **Borrador.** Un dataset válido se guarda como versión `BORRADOR`; publicarlo es un paso aparte.
  Un borrador se puede descartar, con todos sus datos.
- **Diferencias.** Cada importación devuelve altas, cambios y bajas frente a la versión publicada
  del mismo curso o, si no la hay, frente al curso anterior publicado. Los cambios en los módulos
  de una regla se muestran como cambio de la regla (`required_modules`).
- **Arranque local.** Con Docker Compose, si la base de datos está vacía se importan y publican
  los cursos de `data/sample/`. Si ya hay algún dataset, no se toca nada. Esta carga está
  **desactivada por defecto** en `application.yml`; solo `docker-compose.yml` la activa, con la
  variable `CONVALIDAFP_BOOTSTRAP_SAMPLE_DATASET_ENABLED`.

Para volver a cargar el ejemplo desde cero: `docker compose down -v` y `docker compose up --build`.

## Persistencia

El esquema lo crea Flyway al arrancar (`backend/src/main/resources/db/migration/`) y refleja
[CONTRACT.md](CONTRACT.md). Hibernate solo comprueba que las entidades encajan con él.

- **Versiones de dataset.** Cada curso se guarda como una versión (`dataset_version`) en estado
  `BORRADOR`, `PUBLICADO` o `ARCHIVADO`. La base de datos garantiza una sola versión publicada
  por curso. Al republicar un curso, la versión publicada anterior se archiva: no se borra.
- **Publicar y restaurar.** `DatasetPublicationService.publish` publica un borrador o restaura una
  versión archivada, y archiva la que estuviera publicada en ese curso. Es una sola transacción:
  si la publicación falla, la versión anterior sigue publicada.
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

## Decisiones acordadas para fases siguientes

Acordadas con el dueño al cerrar la Fase 2. El aviso del importador (A3) ya está hecho en la Fase 3.

- **Fase 6 (frontend).** Cuando el resultado viene limitado por el tope (`capped`), la lista de
  asignaturas se titula «Reconocibles hasta el tope de X ECTS» en lugar de «Reconocidas», y se
  muestra el aviso de que la universidad decide cuáles entran hasta el límite.
- **Motor, sin cambios.** Una regla condicionada no se muestra si su asignatura ya está reconocida
  sin condiciones.

## Forma de trabajo

Una fase por sesión y **un commit por fase**, con el build y los tests en verde.
