-- =====================================================================
-- V4__performance_indexes_and_config.sql
-- Tribo Invest Play — Índices de performance e configurações
-- =====================================================================

-- ── Índices para performance ─────────────────────────────────────────

-- Courses: busca por status e ordenação
CREATE INDEX IF NOT EXISTS idx_courses_status_sort ON courses(status, sort_order);

-- Courses: busca por featured
CREATE INDEX IF NOT EXISTS idx_courses_featured ON courses(is_featured) WHERE is_featured = true;

-- Modules: busca por curso
CREATE INDEX IF NOT EXISTS idx_modules_course_id ON modules(course_id, sort_order);

-- Lessons: busca por módulo
CREATE INDEX IF NOT EXISTS idx_lessons_module_id ON lessons(module_id, sort_order);

-- Lessons: busca por status
CREATE INDEX IF NOT EXISTS idx_lessons_status ON lessons(status);

-- LessonProgress: busca por usuário (endpoint mais chamado — a cada 30s)
CREATE INDEX IF NOT EXISTS idx_lesson_progress_user_id ON lesson_progress(user_id);

-- LessonProgress: busca por usuário + aula (upsert)
CREATE INDEX IF NOT EXISTS idx_lesson_progress_user_lesson ON lesson_progress(user_id, lesson_id);

-- LessonProgress: Continue Assistindo
CREATE INDEX IF NOT EXISTS idx_lesson_progress_last_watched ON lesson_progress(user_id, last_watched_at DESC);

-- Subscriptions: verificação de acesso (chamada em todo request autenticado)
CREATE INDEX IF NOT EXISTS idx_subscriptions_user_status ON subscriptions(user_id, status);

-- Users: busca por email (login)
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- course_favorites: busca por usuário
CREATE INDEX IF NOT EXISTS idx_favorites_user_id ON course_favorites(user_id);

-- ── Configuração padrão do video_provider ────────────────────────────
-- Garante que todas as aulas novas usem safevideo como padrão

ALTER TABLE lessons
    ALTER COLUMN video_provider SET DEFAULT 'safevideo';

-- ── Coluna metadata nas aulas ────────────────────────────────────────
-- Permite armazenar dados extras como timestamps de capítulos, recursos, etc.

ALTER TABLE lessons
    ADD COLUMN IF NOT EXISTS metadata jsonb;

-- ── Coluna last_login nos usuários ───────────────────────────────────
-- Rastreia quando o aluno fez login pela última vez

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE;

-- ── Tabela de notificações ───────────────────────────────────────────
-- Para avisar alunos sobre novas aulas, conquistas, etc.

CREATE TABLE IF NOT EXISTS notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(50) NOT NULL,  -- 'new_lesson', 'achievement', 'system'
    title       VARCHAR(255) NOT NULL,
    message     TEXT,
    is_read     BOOLEAN NOT NULL DEFAULT false,
    data        JSONB,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON notifications(user_id, is_read);

-- ── Tabela de conquistas (badges) ────────────────────────────────────

CREATE TABLE IF NOT EXISTS achievements (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(100) NOT NULL,  -- 'first_lesson', 'course_complete', 'streak_7', etc.
    metadata    JSONB,
    earned_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(user_id, type)
);

CREATE INDEX IF NOT EXISTS idx_achievements_user_id ON achievements(user_id);

-- ── Verificação final ────────────────────────────────────────────────

SELECT
    schemaname,
    tablename,
    indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('courses', 'modules', 'lessons', 'lesson_progress', 'subscriptions', 'users', 'course_favorites')
ORDER BY tablename, indexname;
