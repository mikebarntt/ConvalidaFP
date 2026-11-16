# Contrato de datos de ConvalidaFP

Este documento define el formato exacto de un dataset de ConvalidaFP. Es la referencia para quien
prepara los datos (Fase E), para el importador (Fase 3) y para la persistencia (Fase 1).

Un dataset son **9 ficheros CSV más un `manifest.json`**, todos en la carpeta de un curso. Añadir
una universidad, un ciclo o un grado es añadir filas; el esquema no cambia.

> Los puntos marcados con **[interpretación]** no estaban fijados en la especificación. El dueño
> los confirmó el 13/11/2026 y forman parte del contrato.

## 1. Ubicación

```
data/<origen>/<curso>/
```

- `<origen>` es `real` (datos extraídos de documentos oficiales) o `sample` (datos ficticios).
- `<curso>` es el curso académico con formato `AAAA-AA`, por ejemplo `2026-27`.
- Ejemplos: `data/real/2026-27/`, `data/sample/2026-27/`.

Cada carpeta de curso es un dataset completo y autónomo: contiene los 9 ficheros aunque alguno solo
tenga la cabecera. **[interpretación]**

## 2. Formato de los CSV

| Aspecto | Regla |
|---|---|
| Codificación | UTF-8 **sin BOM** |
| Separador | Coma (`,`) |
| Comillas | Según RFC 4180: un campo con coma, comillas o salto de línea va entre comillas dobles, y las comillas interiores se duplican (`"Módulo ""Demo"", XML"`) |
| Cabecera | Obligatoria, con los nombres y el orden exactos de este documento |
| Fin de línea | LF o CRLF **[interpretación]** |
| Decimales | Con punto (`4.5`), nunca con coma; como máximo 2 decimales **[interpretación]** |
| Booleanos | `true` o `false`, en minúsculas **[interpretación]** |
| Fechas | `AAAA-MM-DD` **[interpretación]** |
| Celda vacía | Significa **desconocido**. Nunca se escribe `0` para decir «no se sabe» |

### Identificadores

- En mayúsculas, con cifras y guiones: `UEJ`, `CF-EJ-M01`, `R-001`. Patrón: `[A-Z0-9]+(-[A-Z0-9]+)*`.
- **Estables entre cursos**: la misma entidad conserva su identificador de un curso al siguiente,
  aunque cambie de nombre.
- **Nunca se reutilizan**: un identificador retirado no se asigna a otra entidad.
- Únicos dentro de su fichero en un mismo dataset.

## 3. Ficheros

En las tablas, **Oblig.** indica si la celda puede quedar vacía. «Sí» = no puede estar vacía.

### 3.1 `universities.csv`

| Columna | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `university_id` | Identificador | Sí | **Clave primaria** |
| `name` | Texto | Sí | Nombre oficial completo |
| `short_name` | Texto | No | Siglas |
| `ownership` | `PUBLICA` / `PRIVADA` | Sí | Titularidad |
| `region` | Texto | No | Comunidad autónoma |
| `website_url` | URL | No | Web oficial |

### 3.2 `fp_cycles.csv`

Un mismo ciclo con dos normativas distintas (título antiguo y título actualizado) son **dos filas**
con identificadores distintos.

| Columna | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `cycle_id` | Identificador | Sí | **Clave primaria** |
| `name` | Texto | Sí | Nombre del ciclo |
| `acronym` | Texto | No | Siglas |
| `professional_family` | Texto | No | Familia profesional |
| `regulation_ref` | Texto | No | Norma que regula el título; distingue las dos versiones de un mismo ciclo |
| `total_ects` | Decimal > 0 | No | ECTS totales del ciclo |
| `help_text` | Texto, máximo 500 caracteres | No | Una frase que explica cómo saber si este es tu ciclo. La pantalla de inicio la muestra solo cuando otro ciclo tiene el mismo nombre y distinta `regulation_ref`. Vacío = no se muestra ninguna ayuda (el frontend nunca escribe una propia). Añadida en la Fase 7 |

### 3.3 `fp_modules.csv`

| Columna | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `module_id` | Identificador | Sí | **Clave primaria** |
| `cycle_id` | Identificador | Sí | Clave foránea a `fp_cycles.csv` |
| `official_code` | Texto | No | Código oficial del módulo; permite cruzar módulos compartidos entre ciclos |
| `name` | Texto | Sí | Nombre del módulo |
| `ects` | Decimal > 0 | No | ECTS del módulo |
| `course_year` | Entero > 0 | No | Curso en el que se imparte |

### 3.4 `degrees.csv`

Un grado impartido en dos centros con tablas distintas son **dos filas**.

| Columna | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `degree_id` | Identificador | Sí | **Clave primaria** |
| `university_id` | Identificador | Sí | Clave foránea a `universities.csv` |
| `name` | Texto | Sí | Nombre del grado |
| `degree_type` | `SIMPLE` / `DOBLE` | Sí | Grado simple o doble grado |
| `center` | Texto | No | Centro que lo imparte |
| `total_ects` | Decimal > 0 | Solo si el grado tiene una tabla `VIGENTE` | Carga total. El tope del 25 % se calcula sobre este valor |
| `plan_code` | Texto | No | Código del plan de estudios |
| `url` | URL | No | Página oficial del grado |
| `subjects_complete` | Booleano | No | `true` si `degree_subjects.csv` contiene **todas** las asignaturas del grado; `false` si solo contiene las que aparecen en las tablas de reconocimiento. Vacío = `false`. Con `false`, la simulación no muestra la lista de asignaturas restantes, sino los ECTS pendientes (`total_ects` menos los reconocidos) y un enlace a `url` para consultar el plan de estudios completo. Añadida antes de la Fase E |

### 3.5 `degree_subjects.csv`

| Columna | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `subject_id` | Identificador | Sí | **Clave primaria** |
| `degree_id` | Identificador | Sí | Clave foránea a `degrees.csv` |
| `name` | Texto | Sí | Nombre de la asignatura |
| `ects` | Decimal > 0 | Solo si es destino de una regla `SUBJECT` sin `recognized_ects` | ECTS de la asignatura |
| `subject_type` | `BASICA` / `OBLIGATORIA` / `OPTATIVA` / `PRACTICAS_EXTERNAS` / `TFG` | Sí | Tipo |
| `course_year` | Entero > 0 | No | Curso |
| `official_code` | Texto | No | Código oficial de la asignatura |

### 3.6 `source_documents.csv`

| Columna | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `source_id` | Identificador | Sí | **Clave primaria** |
| `document_type` | `CONVENIO` / `ADENDA` / `TABLA_UNIVERSIDAD` / `BOLETIN_OFICIAL` / `OTRO` | Sí | Tipo de documento |
| `title` | Texto | Sí | Título del documento |
| `issuer` | Texto | No | Quién lo publica |
| `publication_date` | Fecha | No | Fecha de publicación |
| `url` | URL | No | Dónde se descargó |
| `file_sha256` | SHA-256 (64 caracteres hexadecimales en minúsculas) | No | Huella del PDF; detecta cuándo cambia |
| `retrieved_at` | Fecha | No | Cuándo se descargó |

### 3.7 `recognition_tables.csv`

Una fila por combinación de ciclo, grado y curso.

- **Sin fila** = sin datos (`NO_DATA`): no se ha comprobado.
- **`SIN_CONVENIO`** = se ha verificado que no existe tabla firmada (`NO_AGREEMENT`).

| Columna | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `table_id` | Identificador | Sí | **Clave primaria** |
| `cycle_id` | Identificador | Sí | Clave foránea a `fp_cycles.csv` |
| `degree_id` | Identificador | Sí | Clave foránea a `degrees.csv` |
| `academic_year` | `AAAA-AA` | Sí | Curso; coincide con el del manifiesto **[interpretación]** |
| `status` | `VIGENTE` / `SIN_CONVENIO` | Sí | Estado |
| `source_id` | Identificador | Solo si `VIGENTE` | Clave foránea a `source_documents.csv`. Fuente por defecto de sus reglas |
| `table_max_ects` | Decimal > 0 | No | Tope propio de la tabla. Solo puede bajar el límite: se aplica el menor entre este valor y el 25 % |
| `notes` | Texto | No | Observaciones |

### 3.8 `recognition_rules.csv`

| Columna | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `rule_id` | Identificador | Sí | **Clave primaria** |
| `table_id` | Identificador | Sí | Clave foránea a `recognition_tables.csv` |
| `target_type` | `SUBJECT` / `OPTATIVE_CREDITS` | Sí | Qué se reconoce |
| `target_subject_id` | Identificador | Solo si `SUBJECT` | Clave foránea a `degree_subjects.csv`. Vacío en `OPTATIVE_CREDITS` |
| `recognized_ects` | Decimal > 0 | Solo si `OPTATIVE_CREDITS` | En `SUBJECT`, vacío = los ECTS de la asignatura |
| `is_conditional` | Booleano | Sí | `true` si el reconocimiento depende de una condición |
| `condition_text` | Texto | No | La condición, tal como la expresa el documento. Si la regla es condicionada y el documento no la explica, queda vacío: el importador da un aviso y la interfaz muestra «condición no especificada en el documento» |
| `source_id` | Identificador | No | Clave foránea a `source_documents.csv`. Vacío = la regla hereda la fuente de su tabla. Informado = la regla cita ese documento (por ejemplo, una adenda) |
| `source_page` | Entero > 0 | Sí | Página de la **fuente efectiva** de la regla: la suya si tiene `source_id`, y si no la de su tabla |
| `source_excerpt` | Texto, máximo 200 caracteres | No | Fragmento literal breve del PDF que respalda la regla |

### 3.9 `rule_requirements.csv`

Módulos que exige cada regla. **Clave primaria compuesta**: (`rule_id`, `module_id`).

Una regla sin ninguna fila en este fichero es válida: reconoce por el mero hecho de tener el
título. El importador la lista como aviso para revisarla a mano.

| Columna | Tipo | Oblig. | Descripción |
|---|---|---|---|
| `rule_id` | Identificador | Sí | Clave foránea a `recognition_rules.csv` |
| `module_id` | Identificador | Sí | Clave foránea a `fp_modules.csv` |

### 3.10 `manifest.json`

```json
{
  "academic_year": "2026-27",
  "date": "2026-11-13",
  "reviewer": "Nombre de quien revisó el dataset",
  "files": [
    { "name": "universities.csv", "sha256": "…64 caracteres hexadecimales…" }
  ]
}
```

- `academic_year` coincide con el nombre de la carpeta.
- `files` lista exactamente los 9 CSV, cada uno con el SHA-256 de sus bytes.
- Los nombres de los campos del manifiesto son **[interpretación]**; la especificación solo fija su
  contenido (curso, fecha, revisor y checksum de cada fichero).

Para calcular un checksum: `sha256sum universities.csv` (Linux, macOS, Git Bash) o
`Get-FileHash universities.csv -Algorithm SHA256` (PowerShell, pasando el resultado a minúsculas).

## 4. Semántica de las reglas

1. **Y dentro de una regla.** Todos los módulos de una misma regla son obligatorios.
2. **O entre reglas.** Dos reglas distintas de la misma tabla con la misma asignatura destino son
   alternativas; la asignatura cuenta **una sola vez**. Como fuente se conserva la regla de menor
   `rule_id`.
3. **`OPTATIVE_CREDITS`** reconoce créditos optativos sin asignatura concreta. Si una tabla tiene
   varias reglas de este tipo, **se suman**.
4. **Reglas condicionadas** (`is_conditional` = `true`, por ejemplo prácticas externas solo si son
   de naturaleza similar): se muestran aparte y **no suman** al total.
5. **`source_excerpt`** es un fragmento literal del PDF, nunca una paráfrasis.
6. **Fuente efectiva.** La fuente de una regla es su `source_id` si lo tiene y, si no, el de su
   tabla. `source_page` y `source_excerpt` se refieren siempre a esa fuente.
7. **Regla aplicable.** Una regla se aplica si el estudiante tiene todos sus módulos. Una regla sin
   módulos se aplica siempre. En la v1 se asume que el estudiante tiene todos los módulos del
   ciclo, pero el motor recibe el conjunto de módulos como parámetro.
8. **Tope.** El total reconocido es el menor de tres valores:

   ```
   ECTS reconocidos = min( suma de las reglas, 0.25 × total_ects del grado, table_max_ects )
   ```

   El motor nunca descarta asignaturas: si la suma supera el tope, limita el total y lo marca
   (`capped = true`). El 0.25 es un parámetro de configuración
   (`convalidafp.simulation.max-recognition-ratio`), no un número fijo en el código. Su base legal
   es la normativa de reconocimiento de créditos de la UAM y el convenio entre la Comunidad de
   Madrid y la UAH (BOCM 23/02/2026); la v1 solo cubre Madrid.

### Ejemplo (ficticio)

`recognition_rules.csv`:

```csv
rule_id,table_id,target_type,target_subject_id,recognized_ects,is_conditional,condition_text,source_id,source_page,source_excerpt
R-001,T-EJ-01,SUBJECT,UEJ-GP-PROG1,,false,,,3,Módulo Ficticio 01 - Programación I
R-002,T-EJ-01,SUBJECT,UEJ-GP-BD,,false,,,3,Módulos Ficticios 02 y 03 - Bases de Datos
R-003,T-EJ-01,OPTATIVE_CREDITS,,6,false,,,4,6 créditos optativos
R-004,T-EJ-01,SUBJECT,UEJ-GP-PRACT,,true,Solo si las prácticas son de naturaleza similar,,4,Formación en empresa - Prácticas externas
R-053,T-EJ-01,SUBJECT,UEJ-GP-IS1,,false,,S-EJ-04,2,Módulo Ficticio 05 - Ingeniería del Software I
```

`rule_requirements.csv`:

```csv
rule_id,module_id
R-001,CF-EJ-M01
R-002,CF-EJ-M02
R-002,CF-EJ-M03
R-003,CF-EJ-M04
R-004,CF-EJ-FCT
```

R-002 exige dos módulos (Y). R-004 es condicionada: aparece aparte y no suma. R-053 cita su propia
fuente (S-EJ-04, página 2) en lugar de la de su tabla.

## 5. Validaciones

El importador distingue dos niveles. Ambos indican el código de la validación (los de las tablas de
esta sección), fichero, fila, columna y motivo.

- **Errores**: bloquean la carga. Basta uno solo para rechazar la carga entera.
- **Avisos**: no bloquean la carga. Se muestran en el panel de administración antes de publicar,
  para revisarlos a mano.

### 5.1 Errores

De la especificación:

| # | Validación |
|---|---|
| V1 | Claves foráneas íntegras |
| V2 | Ninguna clave duplicada |
| V3 | Todos los ECTS informados son mayores que 0 |
| V4 | `target_subject_id` pertenece al grado de la tabla de la regla |
| V5 | Los módulos de `rule_requirements.csv` pertenecen al ciclo de la tabla de la regla |
| V6 | Toda tabla `VIGENTE` tiene `source_id` |
| V7 | Toda tabla `VIGENTE` tiene al menos una regla |
| V8 | Toda regla tiene `source_page` |
| V9 | Una sola tabla por combinación de ciclo, grado y curso |
| V10 | Ninguna regla duplicada: misma tabla, mismo destino y mismo conjunto de módulos |
| V11 | Todo grado con una tabla `VIGENTE` tiene `total_ects` |
| V12 | Toda asignatura destino de una regla `SUBJECT` sin `recognized_ects` tiene `ects` |

De formato **[interpretación]**:

| # | Validación |
|---|---|
| F1 | Están los 9 ficheros y el `manifest.json`; UTF-8 válido y sin BOM |
| F2 | La cabecera de cada fichero es exactamente la de este documento |
| F3 | Todas las filas tienen el mismo número de columnas que la cabecera |
| F4 | Los identificadores cumplen el patrón de mayúsculas, cifras y guiones |
| F5 | Los valores de lista (`ownership`, `degree_type`, `subject_type`, `document_type`, `status`, `target_type`, `is_conditional`) son uno de los permitidos, y `subjects_complete`, si está informado, es `true` o `false` |
| F6 | Decimales, enteros, fechas y SHA-256 tienen el formato indicado, y ningún texto supera la longitud máxima (64 caracteres en identificadores, códigos y siglas; 255 en nombres; 500 en títulos y en `help_text`; 1000 en URL y condiciones; 2000 en notas) |
| F7 | Las columnas marcadas como obligatorias no están vacías |
| F8 | El checksum de cada fichero coincide con el del manifiesto, y el curso del manifiesto con la carpeta y con `academic_year` de todas las tablas |
| F9 | Una regla `OPTATIVE_CREDITS` no tiene `target_subject_id` y sí `recognized_ects` |
| F10 | Una tabla `SIN_CONVENIO` no tiene reglas |
| F11 | `source_excerpt` no supera los 200 caracteres |

### 5.2 Avisos

| # | Aviso |
|---|---|
| A1 | Regla condicionada (`is_conditional` = `true`) con `condition_text` vacío |
| A2 | Regla sin módulos en `rule_requirements.csv` (reconocimiento por tener el título) |
| A3 | Dos reglas alternativas no condicionadas de la misma tabla llevan a la misma asignatura con ECTS distintos. Cuentan los de la regla de menor `rule_id` |

## 6. Datos de ejemplo

Todo dato inventado vive en `data/sample/` y usa nombres inequívocamente ficticios («Universidad de
Ejemplo», «Grado de Prueba», «Módulo Ficticio 01»). Nunca aparece un nombre de universidad real en
`data/sample/`, y nunca un dato sin documento fuente en `data/real/`.

El dataset de ejemplo y la correspondencia con los 15 casos límite están descritos en
[data/sample/README.md](data/sample/README.md).
