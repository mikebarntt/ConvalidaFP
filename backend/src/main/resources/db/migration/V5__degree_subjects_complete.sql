-- Indica si degree_subjects.csv trae todas las asignaturas del grado (true) o solo las que aparecen
-- en las tablas de reconocimiento (false). Columna subjects_complete de degrees.csv; vacío = false.
-- Las filas que ya existían quedan en false: no se sabe si su lista de asignaturas está completa.
ALTER TABLE degree ADD COLUMN subjects_complete BOOLEAN NOT NULL DEFAULT FALSE;
