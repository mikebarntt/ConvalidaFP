-- Tercer estado de una versión de dataset: ARCHIVADO.
--
-- Al republicar un curso, la versión publicada anterior se archiva en lugar de borrarse: deja de
-- ser la vigente pero conserva sus datos, de modo que la auditoría y las lecturas ya fijadas a ella
-- siguen funcionando. Sigue habiendo una sola versión PUBLICADO por curso.

ALTER TABLE dataset_version
  ADD COLUMN archived_at DATETIME(6) NULL AFTER published_at,
  DROP CONSTRAINT ck_dataset_version_status,
  DROP CONSTRAINT ck_dataset_version_published_at,
  ADD CONSTRAINT ck_dataset_version_status CHECK (status IN ('BORRADOR', 'PUBLICADO', 'ARCHIVADO')),
  -- Un borrador nunca se ha publicado; una versión publicada o archivada, sí.
  ADD CONSTRAINT ck_dataset_version_published_at CHECK ((status = 'BORRADOR') = (published_at IS NULL)),
  ADD CONSTRAINT ck_dataset_version_archived_at CHECK ((status = 'ARCHIVADO') = (archived_at IS NOT NULL));
