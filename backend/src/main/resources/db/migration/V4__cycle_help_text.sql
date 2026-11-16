-- Fase 7: texto opcional que ayuda a distinguir dos ciclos con el mismo nombre y distinta normativa
-- (columna help_text de fp_cycles.csv).
ALTER TABLE fp_cycle ADD COLUMN help_text VARCHAR(500) NULL;
