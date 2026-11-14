-- ConvalidaFP — esquema inicial (Fase 1).
--
-- Refleja CONTRACT.md. Convenciones:
--   * Cada curso se guarda como una versión de dataset (dataset_version). Todas las filas de datos
--     llevan dataset_version_id: una lectura se fija a una única versión filtrando por esa columna.
--   * `id` es la clave técnica. `code` es el identificador del contrato (university_id, cycle_id...),
--     único dentro de una versión y estable entre cursos.
--   * Las claves foráneas entre datos son compuestas (id, dataset_version_id): la base de datos
--     impide que una fila apunte a datos de otra versión.
--   * Toda cifra de ECTS es DECIMAL(6,2). NULL significa «desconocido» (celda vacía en el CSV).

CREATE TABLE dataset_version (
  id                      BIGINT       NOT NULL AUTO_INCREMENT,
  academic_year           VARCHAR(7)   NOT NULL,
  status                  VARCHAR(16)  NOT NULL,
  manifest_date           DATE         NULL,
  reviewer                VARCHAR(255) NULL,
  created_at              DATETIME(6)  NOT NULL,
  published_at            DATETIME(6)  NULL,
  -- Solo tiene valor en las versiones publicadas: el índice único garantiza una sola versión
  -- publicada por curso y permite cualquier número de borradores.
  published_academic_year VARCHAR(7) AS (IF(status = 'PUBLICADO', academic_year, NULL)) PERSISTENT,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dataset_version_published (published_academic_year),
  KEY ix_dataset_version_year (academic_year),
  CONSTRAINT ck_dataset_version_status CHECK (status IN ('BORRADOR', 'PUBLICADO')),
  CONSTRAINT ck_dataset_version_year CHECK (academic_year REGEXP '^[0-9]{4}-[0-9]{2}$'),
  CONSTRAINT ck_dataset_version_published_at
    CHECK ((status = 'PUBLICADO') = (published_at IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE university (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  dataset_version_id BIGINT        NOT NULL,
  code               VARCHAR(64)   NOT NULL,
  name               VARCHAR(255)  NOT NULL,
  short_name         VARCHAR(64)   NULL,
  ownership          VARCHAR(16)   NOT NULL,
  region             VARCHAR(255)  NULL,
  website_url        VARCHAR(1000) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_university_code (dataset_version_id, code),
  UNIQUE KEY uk_university_version (id, dataset_version_id),
  CONSTRAINT fk_university_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_version (id),
  CONSTRAINT ck_university_ownership CHECK (ownership IN ('PUBLICA', 'PRIVADA'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE fp_cycle (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  dataset_version_id  BIGINT       NOT NULL,
  code                VARCHAR(64)  NOT NULL,
  name                VARCHAR(255) NOT NULL,
  acronym             VARCHAR(64)  NULL,
  professional_family VARCHAR(255) NULL,
  regulation_ref      VARCHAR(255) NULL,
  total_ects          DECIMAL(6,2) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fp_cycle_code (dataset_version_id, code),
  UNIQUE KEY uk_fp_cycle_version (id, dataset_version_id),
  CONSTRAINT fk_fp_cycle_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_version (id),
  CONSTRAINT ck_fp_cycle_ects CHECK (total_ects IS NULL OR total_ects > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE fp_module (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  dataset_version_id BIGINT       NOT NULL,
  code               VARCHAR(64)  NOT NULL,
  cycle_id           BIGINT       NOT NULL,
  official_code      VARCHAR(64)  NULL,
  name               VARCHAR(255) NOT NULL,
  ects               DECIMAL(6,2) NULL,
  course_year        INT          NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fp_module_code (dataset_version_id, code),
  UNIQUE KEY uk_fp_module_version (id, dataset_version_id),
  CONSTRAINT fk_fp_module_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_version (id),
  CONSTRAINT fk_fp_module_cycle FOREIGN KEY (cycle_id, dataset_version_id)
    REFERENCES fp_cycle (id, dataset_version_id),
  CONSTRAINT ck_fp_module_ects CHECK (ects IS NULL OR ects > 0),
  CONSTRAINT ck_fp_module_year CHECK (course_year IS NULL OR course_year > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE degree (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  dataset_version_id BIGINT        NOT NULL,
  code               VARCHAR(64)   NOT NULL,
  university_id      BIGINT        NOT NULL,
  name               VARCHAR(255)  NOT NULL,
  degree_type        VARCHAR(16)   NOT NULL,
  center             VARCHAR(255)  NULL,
  total_ects         DECIMAL(6,2)  NULL,
  plan_code          VARCHAR(64)   NULL,
  url                VARCHAR(1000) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_degree_code (dataset_version_id, code),
  UNIQUE KEY uk_degree_version (id, dataset_version_id),
  CONSTRAINT fk_degree_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_version (id),
  CONSTRAINT fk_degree_university FOREIGN KEY (university_id, dataset_version_id)
    REFERENCES university (id, dataset_version_id),
  CONSTRAINT ck_degree_type CHECK (degree_type IN ('SIMPLE', 'DOBLE')),
  CONSTRAINT ck_degree_ects CHECK (total_ects IS NULL OR total_ects > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE degree_subject (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  dataset_version_id BIGINT       NOT NULL,
  code               VARCHAR(64)  NOT NULL,
  degree_id          BIGINT       NOT NULL,
  name               VARCHAR(255) NOT NULL,
  ects               DECIMAL(6,2) NULL,
  subject_type       VARCHAR(32)  NOT NULL,
  course_year        INT          NULL,
  official_code      VARCHAR(64)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_degree_subject_code (dataset_version_id, code),
  UNIQUE KEY uk_degree_subject_version (id, dataset_version_id),
  CONSTRAINT fk_degree_subject_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_version (id),
  CONSTRAINT fk_degree_subject_degree FOREIGN KEY (degree_id, dataset_version_id)
    REFERENCES degree (id, dataset_version_id),
  CONSTRAINT ck_degree_subject_type
    CHECK (subject_type IN ('BASICA', 'OBLIGATORIA', 'OPTATIVA', 'PRACTICAS_EXTERNAS', 'TFG')),
  CONSTRAINT ck_degree_subject_ects CHECK (ects IS NULL OR ects > 0),
  CONSTRAINT ck_degree_subject_year CHECK (course_year IS NULL OR course_year > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE source_document (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  dataset_version_id BIGINT        NOT NULL,
  code               VARCHAR(64)   NOT NULL,
  document_type      VARCHAR(32)   NOT NULL,
  title              VARCHAR(500)  NOT NULL,
  issuer             VARCHAR(255)  NULL,
  publication_date   DATE          NULL,
  url                VARCHAR(1000) NULL,
  file_sha256        CHAR(64)      NULL,
  retrieved_at       DATE          NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_source_document_code (dataset_version_id, code),
  UNIQUE KEY uk_source_document_version (id, dataset_version_id),
  CONSTRAINT fk_source_document_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_version (id),
  CONSTRAINT ck_source_document_type
    CHECK (document_type IN ('CONVENIO', 'ADENDA', 'TABLA_UNIVERSIDAD', 'BOLETIN_OFICIAL', 'OTRO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE recognition_table (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  dataset_version_id BIGINT        NOT NULL,
  code               VARCHAR(64)   NOT NULL,
  cycle_id           BIGINT        NOT NULL,
  degree_id          BIGINT        NOT NULL,
  academic_year      VARCHAR(7)    NOT NULL,
  status             VARCHAR(16)   NOT NULL,
  source_id          BIGINT        NULL,
  table_max_ects     DECIMAL(6,2)  NULL,
  notes              VARCHAR(2000) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recognition_table_code (dataset_version_id, code),
  UNIQUE KEY uk_recognition_table_version (id, dataset_version_id),
  -- Una sola tabla por combinación de ciclo, grado y curso (el curso es el de la versión).
  UNIQUE KEY uk_recognition_table_combination (dataset_version_id, cycle_id, degree_id),
  CONSTRAINT fk_recognition_table_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_version (id),
  CONSTRAINT fk_recognition_table_cycle FOREIGN KEY (cycle_id, dataset_version_id)
    REFERENCES fp_cycle (id, dataset_version_id),
  CONSTRAINT fk_recognition_table_degree FOREIGN KEY (degree_id, dataset_version_id)
    REFERENCES degree (id, dataset_version_id),
  CONSTRAINT fk_recognition_table_source FOREIGN KEY (source_id, dataset_version_id)
    REFERENCES source_document (id, dataset_version_id),
  CONSTRAINT ck_recognition_table_status CHECK (status IN ('VIGENTE', 'SIN_CONVENIO')),
  CONSTRAINT ck_recognition_table_source CHECK (status <> 'VIGENTE' OR source_id IS NOT NULL),
  CONSTRAINT ck_recognition_table_ects CHECK (table_max_ects IS NULL OR table_max_ects > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE recognition_rule (
  id                 BIGINT        NOT NULL AUTO_INCREMENT,
  dataset_version_id BIGINT        NOT NULL,
  code               VARCHAR(64)   NOT NULL,
  table_id           BIGINT        NOT NULL,
  target_type        VARCHAR(32)   NOT NULL,
  target_subject_id  BIGINT        NULL,
  recognized_ects    DECIMAL(6,2)  NULL,
  is_conditional     BOOLEAN       NOT NULL,
  condition_text     VARCHAR(1000) NULL,
  -- Opcional: si es NULL, la regla hereda la fuente de su tabla.
  source_id          BIGINT        NULL,
  source_page        INT           NOT NULL,
  source_excerpt     VARCHAR(200)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recognition_rule_code (dataset_version_id, code),
  UNIQUE KEY uk_recognition_rule_version (id, dataset_version_id),
  CONSTRAINT fk_recognition_rule_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_version (id),
  CONSTRAINT fk_recognition_rule_table FOREIGN KEY (table_id, dataset_version_id)
    REFERENCES recognition_table (id, dataset_version_id),
  CONSTRAINT fk_recognition_rule_subject FOREIGN KEY (target_subject_id, dataset_version_id)
    REFERENCES degree_subject (id, dataset_version_id),
  CONSTRAINT fk_recognition_rule_source FOREIGN KEY (source_id, dataset_version_id)
    REFERENCES source_document (id, dataset_version_id),
  CONSTRAINT ck_recognition_rule_type CHECK (target_type IN ('SUBJECT', 'OPTATIVE_CREDITS')),
  CONSTRAINT ck_recognition_rule_target CHECK (
    (target_type = 'SUBJECT' AND target_subject_id IS NOT NULL)
    OR (target_type = 'OPTATIVE_CREDITS' AND target_subject_id IS NULL AND recognized_ects IS NOT NULL)),
  CONSTRAINT ck_recognition_rule_ects CHECK (recognized_ects IS NULL OR recognized_ects > 0),
  CONSTRAINT ck_recognition_rule_page CHECK (source_page > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Módulos que exige cada regla. Clave primaria compuesta (rule_id, module_id), como en el contrato.
CREATE TABLE rule_requirement (
  rule_id            BIGINT NOT NULL,
  module_id          BIGINT NOT NULL,
  dataset_version_id BIGINT NOT NULL,
  PRIMARY KEY (rule_id, module_id),
  CONSTRAINT fk_rule_requirement_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_version (id),
  CONSTRAINT fk_rule_requirement_rule FOREIGN KEY (rule_id, dataset_version_id)
    REFERENCES recognition_rule (id, dataset_version_id),
  CONSTRAINT fk_rule_requirement_module FOREIGN KEY (module_id, dataset_version_id)
    REFERENCES fp_module (id, dataset_version_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
