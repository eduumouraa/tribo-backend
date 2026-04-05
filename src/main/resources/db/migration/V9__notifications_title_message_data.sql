-- ══════════════════════════════════════════════════════════════════
-- V9__notifications_title_message_data.sql
-- Adapta tabela notifications para a entidade Java:
--   - Renomeia payload → data
--   - Adiciona title e message
-- ══════════════════════════════════════════════════════════════════

ALTER TABLE notifications
    RENAME COLUMN payload TO data;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS title   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS message TEXT;
