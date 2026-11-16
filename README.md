# ConvalidaFP

Simulador de convalidaciones FP → Grado. Quien ha estudiado un ciclo de FP de grado superior elige
su ciclo y un grado universitario, y ConvalidaFP le dice **qué asignaturas se le reconocen, cuántos
ECTS suman, cuánto le queda por cursar y de qué documento oficial sale cada reconocimiento**
(documento, página y fragmento literal). Puede comparar hasta 4 grados, explorar todos los que
reconocen algo y descargar el resultado en PDF. Los datos son tablas oficiales de reconocimiento
cargadas como datasets versionados desde un panel de administración.

> **Este código lo generó Fable 5.2 (Anthropic) a partir de la especificación del proyecto.** La
> especificación la escribió el dueño del proyecto y es la fuente de verdad; el código se generó
> fase a fase a partir de ella.

> El resultado de una simulación es orientativo y no vinculante: la resolución final corresponde a
> la universidad y el reconocimiento de créditos conlleva tasas.

## Estado

**Fases 0 a 7 cerradas**: contrato y esqueleto, persistencia, motor de reglas, importador, API
pública, administración con seguridad, frontend, y PDF, CI/CD y documentación. Solo queda la Fase E:
extraer los datos reales de los documentos oficiales. Hasta entonces, la aplicación funciona con el
dataset de ejemplo, que es ficticio.

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Contrato de datos, dataset de ejemplo, esqueleto, Docker Compose | Cerrada |
| 1 | Persistencia: esquema, entidades, repositorios y versionado de datasets | Cerrada |
| 2 | Motor de reglas: dominio puro, 15 casos límite, cobertura ≥ 90 % | Cerrada |
| 3 | Importador: validación, borrador, diferencias, publicación versionada | Cerrada |
| 4 | API pública: endpoints, OpenAPI, caché, límite de peticiones, Problem Details | Cerrada |
| 5 | Administración y seguridad: login, importaciones, restauración, auditoría | Cerrada |
| 6 | Frontend: simulador, comparador, explorador, fuentes y panel de administración | Cerrada |
| 7 | Informe PDF, CI/CD, despliegue con HTTPS, ayuda para elegir ciclo, menú móvil, documentación | Cerrada |
| E | Extracción de datos reales | Pendiente |

## Arranque en 3 comandos

Solo hace falta Git y Docker Desktop (o Docker con Compose):

```bash
git clone https://github.com/mikebarntt/ConvalidaFP.git && cd ConvalidaFP
```

```bash
cp .env.example .env
```

```bash
docker compose up --build
```

Este arranque es para **desarrollo local**: carga el dataset de ejemplo (ficticio). Para un servidor
de verdad, ver [Despliegue](#despliegue).

- Simulador: <http://localhost:3000>
- Panel de administración: <http://localhost:3000/admin>
- Salud del backend: <http://localhost:8080/actuator/health> (debe responder `"status":"UP"`)
- Una simulación en la API: <http://localhost:3000/api/v1/simulations?cycle=CF-EJ&degree=UEJ-GP>
- Documentación de la API: <http://localhost:3000/swagger-ui.html>

Para poder entrar al panel de administración, rellena `ADMIN_USERNAME` y `ADMIN_PASSWORD` en `.env`
antes del primer arranque (contraseña de al menos 12 caracteres). Si las dejas vacías no se crea
ningún administrador: no existe ninguna cuenta ni contraseña por defecto.

Para parar: `Ctrl+C` y después `docker compose down` (añade `-v` para borrar también la base de
datos).

Si los puertos 3000 u 8080 están ocupados, cámbialos en `.env`.

## Arquitectura

```
                 Navegador
                     │  HTTPS (producción) · HTTP en local
                     ▼
     ┌──────────────────────────────┐
     │ Caddy (solo en producción)   │  TLS con Let's Encrypt, HSTS, X-Forwarded-For real
     └──────────────┬───────────────┘
                    ▼
     ┌──────────────────────────────┐
     │ frontend · Nginx             │  SPA Vue 3 + proxy de /api, /v3/api-docs y /swagger-ui
     └──────────────┬───────────────┘
                    ▼  /api/v1/**
     ┌──────────────────────────────┐
     │ backend · Spring Boot (Java) │  API pública, PDF, administración, importador,
     │                              │  motor de reglas puro, caché en memoria
     └──────────────┬───────────────┘
                    ▼  JDBC (red interna)
     ┌──────────────────────────────┐
     │ MariaDB                      │  esquema con Flyway, datasets versionados por curso
     └──────────────────────────────┘
```

- **Datos como datasets versionados.** Cada curso es un dataset (9 CSV + `manifest.json`,
  [CONTRACT.md](CONTRACT.md)). Se importa como borrador, se valida, se publica y la versión
  anterior se archiva; se puede restaurar.
- **Motor de reglas puro** (`simulation`): sin Spring ni base de datos, la misma entrada da siempre
  el mismo resultado. La API resuelve una versión publicada, la carga en memoria y le pasa los datos.
- **Una sola procedencia.** La SPA y la API se sirven desde el mismo dominio: sin CORS y sin
  cookies para el visitante (solo los administradores tienen sesión).
- **CI/CD** en GitHub Actions: ver [Integración continua](#integración-continua).

## Cargar un dataset

1. Prepara la carpeta del curso según [CONTRACT.md](CONTRACT.md): `data/real/<curso>/` con los 9
   CSV (UTF-8 sin BOM) y el `manifest.json` con el SHA-256 de cada fichero. Ningún dato sin
   documento fuente.
2. Comprime la carpeta en un ZIP (hasta 25 MB).
3. Entra en `/admin` con un administrador, ve a *Importaciones* y sube el ZIP.
4. Revisa el informe: los **errores** (con código, fichero, fila y columna) rechazan la carga; los
   **avisos** no bloquean, pero conviene leerlos. Revisa también las diferencias con la versión
   publicada.
5. **Publicar**. La web y la API pasan a usar esa versión al instante. Si algo sale mal, en
   *Importaciones* se puede **restaurar** la versión anterior.

En local, `docker compose up` ya carga y publica `data/sample/` si la base de datos está vacía.

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

Frontend (formato, lint, tests unitarios y compilación):

```bash
docker run --rm -v "${PWD}/frontend:/app" -w /app node:24-alpine sh -c "npm ci && npm run format:check && npm run lint && npm test && npm run build"
```

Extremo a extremo (Playwright, en escritorio y en móvil, con comprobación de accesibilidad WCAG 2.1
AA con axe y actualización de las capturas en `docs/screenshots/`). Arranca un entorno aparte en el
puerto 3100 con `.env.e2e`, lanza los tests y después lo borra:

Playwright usa la red del anfitrión para abrir la aplicación en `localhost`: el portapapeles del
navegador solo funciona en un contexto seguro, y `localhost` lo es (en Docker Desktop hay que tener
activada la red del anfitrión, *host networking*).

```bash
docker compose -p convalidafp-e2e --env-file .env.e2e up -d --build --wait
docker run --rm --ipc=host --network host -v "${PWD}:/repo" -w /repo/frontend -e E2E_BASE_URL=http://localhost:3100 mcr.microsoft.com/playwright:v1.65.1-noble npx playwright test
docker compose -p convalidafp-e2e --env-file .env.e2e down -v
```

Configuración de producción (HTTPS, sin puerto 8080, sin datos de ejemplo, `X-Forwarded-For` solo
de proxies de confianza). Arranca `docker-compose.prod.yml` en tu equipo con `localhost` como
dominio, lo comprueba y lo borra; necesita los puertos 80 y 443 libres:

```bash
bash deploy/smoke-test.sh
```

## Integración continua

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) se ejecuta en cada push y en cada pull
request:

| Trabajo | Qué hace |
|---|---|
| Backend | `mvn verify`: tests con Testcontainers (MariaDB real) y cobertura del motor ≥ 90 % |
| Frontend | Formato, lint, tests unitarios y compilación |
| Extremo a extremo | Arranca la aplicación con `.env.e2e` y ejecuta Playwright en escritorio y móvil, con axe |
| Imágenes Docker | Construye las imágenes del backend y del frontend (no las publica) |
| Configuración de producción | Valida el Caddyfile, comprueba `docker-compose.prod.yml` y lanza la prueba de humo |

## Despliegue

Guía paso a paso para un servidor (AWS Lightsail o EC2) en [docs/DEPLOY.md](docs/DEPLOY.md). En
resumen, en el servidor:

```bash
cp .env.prod.example .env.prod
```

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

| Requisito | Cómo lo cumple `docker-compose.prod.yml` |
|---|---|
| HTTPS | Caddy termina TLS (Let's Encrypt), redirige HTTP a HTTPS, envía HSTS y pasa `X-Forwarded-Proto`; las cookies salen `Secure` |
| Sin puerto 8080 | Solo Caddy publica puertos (80 y 443) |
| Sin datos de ejemplo | No define `CONVALIDAFP_BOOTSTRAP_SAMPLE_DATASET_ENABLED` ni monta `data/sample` |
| `X-Forwarded-For` de confianza | Caddy sustituye la cabecera del cliente; el backend solo la acepta de Caddy y Nginx (IP fijas, `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`) |
| Primer administrador | `ADMIN_USERNAME` y `ADMIN_PASSWORD` solo en el primer arranque |

## Estructura

```
backend/           Java 21 + Spring Boot (Maven). Paquete base es.convalidafp
  api/             endpoints públicos, caché y adaptador de las entidades al motor
  catalog/         universidades, ciclos, módulos, grados, asignaturas
  rules/           tablas, reglas y documentos fuente
  simulation/      motor de reglas (dominio puro)
  ingestion/       importador y validación de CSV
  admin/           seguridad, login, importaciones, restauración y auditoría
frontend/          Vue 3 + TypeScript + Vite, Vue Router, Pinia, Tailwind CSS
  src/views/       pantallas públicas y de administración (admin/)
  src/components/  barra de ECTS, aviso orientativo, fuente desplegable, diálogo de confirmación
  src/api/         clientes de la API pública y de administración (con flujo CSRF)
  e2e/             tests de extremo a extremo (Playwright + axe)
docs/screenshots/  capturas de todas las pantallas en escritorio y en móvil
data/sample/       dataset ficticio de ejemplo (dos cursos)
data/real/         datasets reales (vacío hasta la Fase E)
deploy/            Caddyfile y comprobaciones de la configuración de producción
docs/DEPLOY.md     guía de despliegue en AWS Lightsail o EC2
.github/workflows/ integración continua
CONTRACT.md        contrato de datos: ficheros, columnas, semántica y validaciones
docker-compose.yml       desarrollo local (con el dataset de ejemplo)
docker-compose.prod.yml  producción (HTTPS, sin datos de ejemplo)
```

## Frontend

Aplicación de una sola página (Vue 3 + TypeScript) servida por Nginx junto con el proxy a la API.

| Ruta | Pantalla |
|---|---|
| `/` | Inicio: elegir ciclo de FP, universidad y grado (con su estado de tabla) |
| `/resultado?ciclo=&grado=` | Resultado de la simulación, fuentes por asignatura, asignaturas restantes e informe PDF |
| `/comparar?ciclo=&grados=a,b` | Comparador de hasta 4 grados lado a lado |
| `/explorar?ciclo=` | Explorador de los grados que reconocen créditos, de más a menos ECTS |
| `/fuentes` | Fuentes oficiales del curso vigente, última actualización y metodología |
| `/admin/login` | Acceso de administración |
| `/admin/importaciones` | Subir dataset en ZIP, listado de importaciones y gestión de versiones |
| `/admin/importaciones/:id` | Informe de validación (errores con fila, avisos y diferencias) y publicación |
| `/admin/auditoria` | Registro inmutable de acciones y accesos fallidos |

- **Estado en la URL.** La selección del visitante vive en los parámetros de la dirección para que
  baste con copiar el enlace («Copiar enlace») para compartir un resultado, una comparación o una
  búsqueda en el explorador.
- **Trazabilidad y avisos.** Cada reconocimiento despliega el documento oficial, la página y el
  fragmento literal («Ver fuente»). El aviso de que el resultado es orientativo y conlleva tasas
  está siempre visible. Cuando el resultado viene limitado por el tope (`capped`), la lista se
  titula «Reconocibles hasta el tope de X ECTS» y explica qué suma la tabla, cuál es el límite y
  que la universidad decide qué asignaturas entran.
- **Ayuda para elegir el ciclo.** Si dos ciclos tienen el mismo nombre y distinta normativa, cada
  uno muestra en una frase cómo saber cuál es el tuyo. El texto sale del dataset (`help_text` en
  `fp_cycles.csv`); si está vacío no se muestra nada.
- **Informe PDF.** «Descargar informe PDF» en la pantalla de resultado: el mismo desglose, con el
  aviso de que es orientativo.
- **Asignaturas restantes solo con el plan completo.** Si el dataset no trae todas las asignaturas
  del grado (`subjects_complete` = `false` en `degrees.csv`), la pantalla y el PDF sustituyen la
  lista de restantes por «Te quedan X ECTS por cursar» y un enlace a la web del grado (si tiene
  `url`).
- **Menú del móvil** plegado en un botón «Menú» con un desplegable; en pantallas anchas va en línea.
- **Tres resultados claros.** Distingue entre reconocimiento calculado (con barra de ECTS y marca
  del tope), convenio descartado («No existe tabla de reconocimiento firmada») y combinación aún no
  comprobada («Todavía no tenemos datos de esta combinación»).
- **Panel de administración.** Subida de ZIP de hasta 25 MB, informe detallado, publicación de
  borradores y restauración de versiones archivadas siempre con diálogo de confirmación previa. Si
  la sesión caduca, vuelve al login avisándolo y regresa después a la pantalla en la que se estaba.
- **Accesibilidad y privacidad.** Contraste WCAG 2.1 AA verificado automáticamente con axe en todas
  las pantallas, navegación completa con teclado (enlace «Saltar al contenido» y paso del foco al
  cambiar de pantalla), sin desbordamiento horizontal en móvil ni en escritorio, y sin cookies ni
  peticiones a terceros al usar el simulador.

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
| GET | `/api/v1/simulations/report.pdf?cycle=&degree=` | Informe PDF de la simulación (OpenPDF) |
| GET | `/api/v1/comparisons?cycle=&degrees=a,b,c` | Comparación de hasta 4 grados |
| GET | `/api/v1/explore?cycle=` | Grados que reconocen algo, de más a menos ECTS |
| GET | `/api/v1/sources` | Documentos oficiales del curso |
| GET | `/api/v1/sources/{id}` | Documento oficial de una regla |

Todos aceptan `year=AAAA-AA`; sin él se usa el curso vigente publicado.

- **Tres resultados de simulación**, los tres con código 200: `RECOGNIZED`, `NO_AGREEMENT` y
  `NO_DATA`. Todos llevan el aviso de que el resultado es orientativo.
- **`subjectsComplete`.** `false` cuando el dataset solo trae las asignaturas de las tablas de
  reconocimiento: entonces `remainingByYear` va vacío. `pendingEcts` no cambia (`total_ects` del
  grado menos los reconocidos).
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
- **Informe PDF** con el mismo desglose que la pantalla: cifra, tope, asignaturas por curso con su
  fuente (documento, página y fragmento literal), condicionados aparte, restantes y el aviso. Se
  descarga como `convalidafp-<ciclo>-<grado>.pdf`, con las mismas cabeceras de caché; los errores
  siguen en Problem Details.
- **OpenAPI** en `/v3/api-docs` y Swagger UI en `/swagger-ui.html`, también a través de Nginx.
- **Métrica** `convalidafp.simulation` con el tiempo de cada simulación.

## Administración y seguridad

Está en el paquete `admin`. Solo los administradores tienen cuenta; simular no requiere ninguna.

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/v1/admin/csrf` | Token CSRF (no requiere sesión; hace falta para el login) |
| POST | `/api/v1/admin/login` | Login con los campos de formulario `username` y `password` |
| POST | `/api/v1/admin/logout` | Cerrar sesión |
| GET | `/api/v1/admin/session` | Administrador con la sesión iniciada |
| POST | `/api/v1/admin/imports` | Subir un ZIP (campo `file`); devuelve informe y diferencias; queda en borrador |
| GET | `/api/v1/admin/imports` | Importaciones, sin informe |
| GET | `/api/v1/admin/imports/{id}` | Estado e informe guardado de una importación |
| POST | `/api/v1/admin/imports/{id}/publish` | Publicar el borrador como nueva versión |
| GET | `/api/v1/admin/versions` | Versiones con su estado |
| POST | `/api/v1/admin/versions/{id}/restore` | Restaurar una versión archivada |
| GET | `/api/v1/admin/audit` | Auditoría, lo más reciente primero |

- **Dos zonas.** `/api/v1/admin/**` exige sesión con rol ADMIN: sin login responde 401 y con un
  usuario sin ese rol, 403. Todo lo demás (API pública, documentación, salud) es abierto, sin sesión
  y sin cookies.
- **Sesión y CSRF.** El login crea una sesión en una cookie `HttpOnly` y `SameSite=Strict`. Toda
  petición POST de administración, incluido el login, debe enviar en la cabecera `X-XSRF-TOKEN` el
  valor de la cookie `XSRF-TOKEN`. El login devuelve el token nuevo, porque cambia al iniciar sesión.
- **Primer administrador.** Se crea al arrancar a partir de `ADMIN_USERNAME` y `ADMIN_PASSWORD`,
  solo si no existe ninguno. Sin esas variables no hay administrador. La contraseña se guarda como
  hash bcrypt.
- **Límite de intentos.** Tras 5 fallos de login de un mismo usuario desde una misma IP, o 20
  desde una misma IP con cualquier usuario, los nuevos intentos reciben un 429 durante 15 minutos,
  aunque la contraseña sea correcta. Nadie puede bloquear a un administrador desde otra IP.
- **Tamaño máximo de subida.** 25 MB, tanto en Nginx como en el backend; al superarlo se responde
  413 en Problem Details.
- **Informes guardados.** Cada subida, se acepte o no, queda como una importación con sus errores,
  avisos y diferencias.
- **Auditoría.** Quién, qué acción (`IMPORTAR`, `PUBLICAR`, `RESTAURAR`, `LOGIN_FALLIDO`), sobre
  qué versión, cuándo y desde qué IP. Solo admite altas: la base de datos rechaza cambios y borrados.
- **Errores** en Problem Details, también los 401, 403, 413 y 429.

## Importador

Está en el paquete `ingestion`. Se usa desde el panel de administración (`/admin/importaciones`) y
desde el arranque local.

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

## Decisiones acordadas

Acordadas con el dueño al cerrar la Fase 2 y ya aplicadas (el aviso A3 en la Fase 3 y el título de
la lista limitada en la Fase 6):

- **Frontend.** Cuando el resultado viene limitado por el tope (`capped`), la lista de asignaturas
  se titula «Reconocibles hasta el tope de X ECTS» en lugar de «Asignaturas reconocidas», y se
  muestra el aviso de que la universidad decide cuáles entran hasta el límite.
- **Motor.** Una regla condicionada no se muestra si su asignatura ya está reconocida sin
  condiciones.

## Forma de trabajo

Una fase por sesión y **un commit por fase**, con el build y los tests en verde.
