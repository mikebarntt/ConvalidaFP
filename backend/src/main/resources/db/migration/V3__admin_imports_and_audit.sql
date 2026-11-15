-- ConvalidaFP — administración (Fase 5): administradores, informes de importación y auditoría.

-- Solo los administradores tienen cuenta. La contraseña se guarda como hash (bcrypt), nunca en claro.
CREATE TABLE admin_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(16)  NOT NULL,
  created_at    DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_user_username (username),
  CONSTRAINT ck_admin_user_role CHECK (role IN ('ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Una fila por cada dataset subido, se acepte o no, con su informe completo (errores, avisos y
-- diferencias con lo publicado) en JSON.
CREATE TABLE dataset_import (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  created_at         DATETIME(6)  NOT NULL,
  created_by         VARCHAR(100) NOT NULL,
  file_name          VARCHAR(255) NULL,
  academic_year      VARCHAR(7)   NULL,
  outcome            VARCHAR(16)  NOT NULL,
  -- La versión en borrador que creó la importación. NULL si se rechazó o si el borrador se descartó.
  dataset_version_id BIGINT       NULL,
  error_count        INT          NOT NULL,
  warning_count      INT          NOT NULL,
  report_json        LONGTEXT     NOT NULL,
  PRIMARY KEY (id),
  KEY ix_dataset_import_created (created_at),
  CONSTRAINT fk_dataset_import_version FOREIGN KEY (dataset_version_id) REFERENCES dataset_version (id)
    ON DELETE SET NULL,
  CONSTRAINT ck_dataset_import_outcome CHECK (outcome IN ('ACEPTADA', 'RECHAZADA'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Auditoría: quién hizo qué, sobre qué versión y cuándo.
-- No tiene claves foráneas a propósito: una fila de auditoría no debe cambiar ni impedir nada
-- aunque después se borre aquello a lo que se refiere.
CREATE TABLE audit_event (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  occurred_at        DATETIME(6)  NOT NULL,
  actor              VARCHAR(100) NOT NULL,
  action             VARCHAR(32)  NOT NULL,
  dataset_version_id BIGINT       NULL,
  academic_year      VARCHAR(7)   NULL,
  import_id          BIGINT       NULL,
  detail             VARCHAR(500) NULL,
  ip                 VARCHAR(45)  NULL,
  PRIMARY KEY (id),
  KEY ix_audit_event_occurred (occurred_at),
  CONSTRAINT ck_audit_event_action CHECK (action IN ('IMPORTAR', 'PUBLICAR', 'RESTAURAR', 'LOGIN_FALLIDO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- La auditoría solo admite altas: la propia base de datos rechaza cualquier cambio o borrado.
CREATE TRIGGER audit_event_no_update BEFORE UPDATE ON audit_event FOR EACH ROW
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La auditoria no se puede modificar';

CREATE TRIGGER audit_event_no_delete BEFORE DELETE ON audit_event FOR EACH ROW
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La auditoria no se puede borrar';
