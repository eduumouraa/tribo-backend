-- ══════════════════════════════════════════════════════════════════
-- V7__forum_likes.sql
-- Adiciona rastreamento individual de likes no fórum para evitar
-- double-like. Os contadores desnormalizados (likes_count) nas tabelas
-- community_posts e community_comments são mantidos para performance.
-- ══════════════════════════════════════════════════════════════════

-- Adiciona coluna updated_at em community_posts (para ordenação por atividade)
ALTER TABLE community_posts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Adiciona coluna updated_at em community_comments
ALTER TABLE community_comments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- ── POST_LIKES ────────────────────────────────────────────────────
-- Garante que cada usuário curte um post apenas uma vez
CREATE TABLE IF NOT EXISTS post_likes (
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id     UUID        NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, post_id)
);

CREATE INDEX idx_post_likes_post ON post_likes (post_id);

-- ── COMMENT_LIKES ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS comment_likes (
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    comment_id  UUID        NOT NULL REFERENCES community_comments(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, comment_id)
);

CREATE INDEX idx_comment_likes_comment ON comment_likes (comment_id);

-- ── Índices adicionais para performance ───────────────────────────
CREATE INDEX IF NOT EXISTS idx_posts_author   ON community_posts (author_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_course   ON community_posts (course_id) WHERE course_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_comments_post  ON community_comments (post_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_comments_author ON community_comments (author_id);
