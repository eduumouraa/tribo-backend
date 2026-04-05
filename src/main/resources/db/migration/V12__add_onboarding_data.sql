-- V12__add_onboarding_data.sql
-- Armazena as preferências coletadas no onboarding (nível de experiência + objetivos)
-- como JSON serializado em TEXT para compatibilidade com Hibernate sem tipo específico.

ALTER TABLE users ADD COLUMN IF NOT EXISTS onboarding_data TEXT;
