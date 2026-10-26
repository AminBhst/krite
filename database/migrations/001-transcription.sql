-- Run once before starting the updated API/coordinator/executors on an existing PostgreSQL database.
-- Additive: preserves existing task rows and result object keys. Safe to re-run.
BEGIN;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS transcription_model varchar(255);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS burn_subtitles boolean DEFAULT false;
UPDATE tasks SET burn_subtitles = false WHERE burn_subtitles IS NULL;
ALTER TABLE tasks ALTER COLUMN burn_subtitles SET DEFAULT false;
ALTER TABLE tasks ALTER COLUMN burn_subtitles SET NOT NULL;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS video_file_object_key varchar(255);
-- Hibernate's generated enum CHECK constraint is not reliably expanded by ddl-auto=update.
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_type_check;
ALTER TABLE tasks ADD CONSTRAINT tasks_type_check
    CHECK (type IN ('COMPRESS', 'GENERATE_THUMBNAIL', 'TRANSCRIBE'));
COMMIT;
-- Rollback application binaries without dropping these columns or deleting transcription rows.
-- Older binaries cannot read TRANSCRIBE rows: roll forward, or archive those rows before rollback.
