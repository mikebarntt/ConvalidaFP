# Dataset de ejemplo (ficticio)

Todo lo que hay en `data/sample/` es **inventado**: universidades, ciclos, módulos, grados,
asignaturas, documentos y reglas. Ningún nombre corresponde a una institución real y las URL usan el
dominio reservado `.example`. Sirve para desarrollar y probar; nunca se mezcla con `data/real/`.

Hay dos cursos para poder probar el versionado: `2025-26/` (una sola tabla) y `2026-27/` (completo).

## Resultados esperados (curso 2026-27)

La columna «Suma» es la suma de las reglas no condicionadas, contando cada asignatura una sola vez.
«Reconocido» es el mínimo entre la suma, el 25 % del grado y el tope de la tabla.

| Ciclo | Grado | Tabla | Suma | 25 % del grado | Tope de tabla | Reconocido | ¿Limitado? |
|---|---|---|---|---|---|---|---|
| CF-EJ | UEJ-GP | T-EJ-01 | 51 | 60 | — | 51 | No |
| CF-EJ | UEJ-DG | T-EJ-02 | 75 | 93 | — | 75 | No |
| CF-EJ | UEJ-GP-SUR | T-EJ-03 | 22.5 | 60 | — | 22.5 | No |
| CF-EJ-2024 | UEJ-GP | T-EJ-04 | 39 | 60 | — | 39 | No |
| CF-EJ | UPR-GD | T-PR-01 | 66 | 60 | — | 60 | Sí |
| CF-PR | UPR-GD | T-PR-02 | 36 | 60 | 30 | 30 | Sí |
| CF-EJ | UFI-GS | T-FI-01 | — | — | — | `NO_AGREEMENT` | — |
| CF-PR | UEJ-DG | T-FI-02 | — | — | — | `NO_AGREEMENT` | — |
| cualquier otra combinación | | (sin fila) | — | — | — | `NO_DATA` | — |

En 2025-26, T-EJ-01 solo tiene las reglas R-001 a R-006 y suma 30.

## Dónde está cada caso límite

| # | Caso | Dónde se ve |
|---|---|---|
| 1 | `NO_DATA` frente a `NO_AGREEMENT` | T-FI-01 y T-FI-02 son `SIN_CONVENIO`; CF-PR × UEJ-GP no tiene fila |
| 2 | Asignatura que exige dos módulos (Y) | R-002: CF-EJ-M02 **y** CF-EJ-M03 → UEJ-GP-BD |
| 3 | Misma asignatura por dos reglas (O) | R-006 y R-007 → UEJ-GP-WEB (cuenta 6 ECTS, una vez; fuente R-006) |
| 4 | Un módulo sirve para varias asignaturas | CF-EJ-M01 → UEJ-GP-PROG1 (R-001) y UEJ-GP-PROG2 (R-005) |
| 5 | La suma supera el 25 % | T-PR-01: suma 66, tope 60 |
| 6 | Tope de tabla inferior al 25 % | T-PR-02: `table_max_ects` = 30, suma 36 |
| 7 | Créditos optativos genéricos | R-003 (6), R-028 (4.5), R-044 (12), R-052 (3) |
| 8 | Regla condicionada | R-004, R-024, R-045, R-054 (prácticas externas): aparte, no suman |
| 9 | Doble grado con más de 240 ECTS | UEJ-DG, 372 ECTS: tope 93; la suma (75) superaría el tope de un grado de 240 |
| 10 | Mismo ciclo con dos normativas | CF-EJ y CF-EJ-2024: dos filas, módulos propios, tablas distintas (T-EJ-01 y T-EJ-04) |
| 11 | Asignatura renombrada entre cursos | UEJ-GP-WEB: «Programación Web de Prueba» en 2025-26, «Desarrollo Web de Prueba» en 2026-27 |
| 12 | Mismo grado en dos centros | UEJ-GP y UEJ-GP-SUR, con tablas T-EJ-01 y T-EJ-03 |
| 13 | ECTS con decimales | Asignaturas de 4.5 (UEJ-GP-LM, UEJ-GP-IPO), módulos de 12.5 y 9.5, R-028 |
| 14 | Publicación durante una simulación | No es un caso de datos: se prueba publicando 2025-26 y 2026-27 uno tras otro (Fases 1 y 3) |
| 15 | Tildes, comillas y comas | Universidad UFI, módulo CF-EJ-M06, asignatura UEJ-GP-EXPR, fuente S-EJ-03, regla R-008 |

## Otros casos del contrato

| Caso | Dónde se ve |
|---|---|
| Regla con fuente propia, distinta de la de su tabla | R-053 cita la adenda S-EJ-04; el resto hereda la fuente de su tabla |
| Regla sin módulos (reconocimiento por tener el título) | R-052: 3 créditos optativos. Genera un **aviso** |
| Regla condicionada sin condición en el documento | R-054: `condition_text` vacío. Genera un **aviso** |
| Celda vacía = desconocido | El grado UFI-GS tiene ECTS y curso vacíos y una lista de asignaturas incompleta |
| Lista de asignaturas completa o parcial (`subjects_complete`) | `true` en UEJ-GP, UEJ-DG y UPR-GD (los dos cursos): se muestran las asignaturas restantes. `false` en UEJ-GP-SUR: CF-EJ × UEJ-GP-SUR muestra solo los ECTS pendientes y el enlace a la web del grado. Vacío (= `false`) en UFI-GS, que no tiene `url` |

El dataset 2026-27 no tiene errores y produce exactamente esos dos avisos; el 2025-26, ninguno.

Los tests de `backend/src/test/java/es/convalidafp/contract/` comprueban todo lo anterior.
