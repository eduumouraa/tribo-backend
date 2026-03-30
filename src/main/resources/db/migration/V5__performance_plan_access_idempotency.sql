-- ══════════════════════════════════════════════════════════════════
-- V5 — Performance, controle de acesso por plano e idempotência Stripe
-- ══════════════════════════════════════════════════════════════════

-- ── Controle de acesso por plano nos cursos ──────────────────────
-- Cada curso requer um plano específico para ser assistido.
-- 'free' = sem assinatura necessária | 'tribo' | 'financas' | 'combo'
ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS required_plan VARCHAR(50) NOT NULL DEFAULT 'tribo';

-- Atualiza os cursos existentes com o plano correto
UPDATE courses SET required_plan = 'tribo'
    WHERE slug = 'tribo-do-investidor';

UPDATE courses SET required_plan = 'financas'
    WHERE slug = 'organizacao-financeira-negociacao-dividas';

-- ── Idempotência de webhooks Stripe ─────────────────────────────
-- Stripe pode reenviar o mesmo evento. Esta tabela evita duplo processamento.
CREATE TABLE IF NOT EXISTS stripe_webhook_events (
    event_id     VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Índices para 10k+ usuários simultâneos ───────────────────────

-- Verificação de assinatura ativa (hot path — chamado em todo request autenticado)
-- Com cache Redis de 10min, esse índice é acionado apenas no cache miss
CREATE INDEX IF NOT EXISTS idx_subs_active_user
    ON subscriptions (user_id, status, plan)
    WHERE status = 'ACTIVE';

-- Verificação de acesso por plano em cursos
CREATE INDEX IF NOT EXISTS idx_courses_required_plan
    ON courses (required_plan, status)
    WHERE status = 'PUBLISHED';

-- Progresso por usuário (dashboard do aluno — carregado ao entrar na plataforma)
CREATE INDEX IF NOT EXISTS idx_progress_user_completed
    ON lesson_progress (user_id, is_completed);

CREATE INDEX IF NOT EXISTS idx_progress_user_course_lesson
    ON lesson_progress (user_id, course_id, lesson_id);

-- Password reset tokens — busca por token (chamado no reset de senha)
CREATE INDEX IF NOT EXISTS idx_reset_tokens_token
    ON password_reset_tokens (token)
    WHERE used = false;

-- Cursos publicados em destaque (hero banner — chamado em todo acesso à home)
CREATE INDEX IF NOT EXISTS idx_courses_published_featured
    ON courses (status, is_featured, sort_order)
    WHERE status = 'PUBLISHED';

-- Webhook idempotência — lookup rápido por event_id (já é PK, mas confirma)
-- PK já cria o índice automaticamente no PostgreSQL.

-- ── Configurações de sessão PostgreSQL para alta concorrência ────
-- Configura statement timeout para evitar queries longas travando conexões
ALTER DATABASE tribo SET statement_timeout = '30s';
ALTER DATABASE tribo SET idle_in_transaction_session_timeout = '60s';
ALTER DATABASE tribo SET lock_timeout = '10s';
