-- ══════════════════════════════════════════════════════════════════
-- V8__ranking_materials_ratings.sql
-- Adiciona:
--   1. available_at nas aulas (liberação programada / dripping)
--   2. lesson_materials (arquivos e links por aula)
--   3. course_ratings (avaliação por estrelas por usuário)
--   4. member_points (pontuação de gamificação)
-- ══════════════════════════════════════════════════════════════════

-- ── 1. Liberação programada de aulas ─────────────────────────────
ALTER TABLE lessons
    ADD COLUMN IF NOT EXISTS available_at TIMESTAMPTZ;

COMMENT ON COLUMN lessons.available_at IS
    'Se preenchido, a aula só é visível para alunos a partir desta data/hora. '
    'NULL = disponível imediatamente ao publicar.';

-- ── 2. Materiais de aula ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS lesson_materials (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id   UUID        NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL,
    url         TEXT        NOT NULL,
    type        VARCHAR(20) NOT NULL DEFAULT 'link',
    -- type: 'pdf' | 'spreadsheet' | 'link' | 'image' | 'other'
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lesson_materials_lesson ON lesson_materials (lesson_id, sort_order);

-- ── 3. Avaliação de cursos ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS course_ratings (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id   UUID        NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating      SMALLINT    NOT NULL CHECK (rating >= 1 AND rating <= 5),
    review      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (course_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_course_ratings_course ON course_ratings (course_id);

-- Cache desnormalizado na tabela de cursos para evitar AVG() a cada request
ALTER TABLE courses
    ADD COLUMN IF NOT EXISTS rating_avg  NUMERIC(3,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rating_count INT          NOT NULL DEFAULT 0;

-- ── 4. Pontuação dos membros ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS member_points (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    points      INT         NOT NULL,
    reason      VARCHAR(100) NOT NULL,
    -- reason: 'LESSON_COMPLETE' | 'COURSE_COMPLETE' | 'FORUM_POST' | 'FORUM_COMMENT' | 'ACHIEVEMENT' | 'MANUAL'
    ref_id      UUID,       -- ID da aula/curso/post relacionado (opcional)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_member_points_user ON member_points (user_id, created_at DESC);

-- View auxiliar: total de pontos por usuário (usada pelo ranking)
CREATE OR REPLACE VIEW member_ranking AS
SELECT
    u.id          AS user_id,
    u.name        AS user_name,
    u.avatar_url,
    COALESCE(SUM(mp.points), 0) AS total_points,
    RANK() OVER (ORDER BY COALESCE(SUM(mp.points), 0) DESC) AS rank_position
FROM users u
LEFT JOIN member_points mp ON mp.user_id = u.id
GROUP BY u.id, u.name, u.avatar_url;
