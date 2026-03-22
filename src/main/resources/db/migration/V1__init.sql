-- ══════════════════════════════════════════════════════════════════
-- V1__init.sql — Schema inicial da Tribo Invest Play
-- Flyway executa esse script automaticamente na primeira inicialização
-- ══════════════════════════════════════════════════════════════════

-- ── Extensão para UUID ───────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── USERS ────────────────────────────────────────────────────────
-- Tabela central de usuários. Roles: STUDENT, ADMIN, OWNER, SUPPORT
CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,          -- bcrypt strength 12
    avatar_url      TEXT,
    role            VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                                                    -- ACTIVE | PENDING | SUSPENDED
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_status ON users (status);

-- ── SUBSCRIPTIONS ─────────────────────────────────────────────────
-- Controla o acesso pago. Um usuário pode ter múltiplas assinaturas
-- (histórico), mas só uma ativa por vez.
CREATE TABLE subscriptions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan            VARCHAR(50)  NOT NULL,           -- tribo | financas | combo
    status          VARCHAR(20)  NOT NULL,           -- TRIAL | ACTIVE | CANCELLED | EXPIRED
    provider        VARCHAR(50)  NOT NULL,           -- stripe | manual | eduzz (migração)
    provider_id     VARCHAR(255),                    -- ID do evento no Stripe
    stripe_customer_id VARCHAR(255),                 -- ID do cliente no Stripe
    stripe_subscription_id VARCHAR(255),             -- ID da assinatura no Stripe
    started_at      TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_subs_user   ON subscriptions (user_id, status);
CREATE INDEX idx_subs_stripe ON subscriptions (stripe_subscription_id);

-- ── COURSES ───────────────────────────────────────────────────────
CREATE TABLE courses (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL UNIQUE,    -- URL amigável
    description     TEXT,
    thumbnail_url   TEXT,
    category        VARCHAR(80),
    badge           VARCHAR(50),                     -- "Principal", "Novo", etc
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
                                                    -- DRAFT | PUBLISHED | ARCHIVED
    is_featured     BOOLEAN      NOT NULL DEFAULT false,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    metadata        JSONB,                           -- what_you_learn, requirements
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX idx_courses_status   ON courses (status, is_featured, sort_order);
CREATE INDEX idx_courses_slug     ON courses (slug);
-- Full-text search em português
CREATE INDEX idx_courses_fts ON courses
    USING GIN (to_tsvector('portuguese', title || ' ' || COALESCE(description, '')));

-- ── MODULES ───────────────────────────────────────────────────────
CREATE TABLE modules (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID         NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_modules_course ON modules (course_id, sort_order);

-- ── LESSONS ───────────────────────────────────────────────────────
CREATE TABLE lessons (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id       UUID         NOT NULL REFERENCES modules(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    video_key       TEXT,        -- ID no Panda Video ou chave no S3
    video_provider  VARCHAR(20)  NOT NULL DEFAULT 'panda', -- panda | s3
    duration_secs   INTEGER      NOT NULL DEFAULT 0,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    is_preview      BOOLEAN      NOT NULL DEFAULT false,   -- aula gratuita sem login
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_lessons_module ON lessons (module_id, sort_order);

-- ── LESSON_PROGRESS ───────────────────────────────────────────────
-- Tabela mais acessada da plataforma — atualizada a cada 30s pelo player
-- Redis atua como buffer antes de persistir aqui
CREATE TABLE lesson_progress (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_id           UUID        NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    course_id           UUID        NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    watched_seconds     INTEGER     NOT NULL DEFAULT 0,
    is_completed        BOOLEAN     NOT NULL DEFAULT false,
    completed_at        TIMESTAMPTZ,
    last_watched_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, lesson_id)
);

-- Índices críticos para performance do dashboard
CREATE INDEX idx_progress_user_course   ON lesson_progress (user_id, course_id);
CREATE INDEX idx_progress_last_watched  ON lesson_progress (user_id, last_watched_at DESC);

-- ── COURSE_FAVORITES ──────────────────────────────────────────────
CREATE TABLE course_favorites (
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id   UUID        NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, course_id)
);

-- ── COMMUNITY_POSTS ───────────────────────────────────────────────
CREATE TABLE community_posts (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id   UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id   UUID        REFERENCES courses(id) ON DELETE SET NULL,
    title       VARCHAR(255),
    body        TEXT        NOT NULL,
    likes_count INTEGER     NOT NULL DEFAULT 0,
    pinned      BOOLEAN     NOT NULL DEFAULT false,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_posts_feed ON community_posts (created_at DESC) WHERE status = 'ACTIVE';

-- ── COMMUNITY_COMMENTS ────────────────────────────────────────────
CREATE TABLE community_comments (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id     UUID        NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
    author_id   UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id   UUID        REFERENCES community_comments(id) ON DELETE CASCADE,
    body        TEXT        NOT NULL,
    likes_count INTEGER     NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── NOTIFICATIONS ─────────────────────────────────────────────────
CREATE TABLE notifications (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(80) NOT NULL,   -- new_lesson | payment | community_reply
    payload     JSONB,
    is_read     BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user ON notifications (user_id, is_read, created_at DESC);

-- ── AUDIT_LOGS ────────────────────────────────────────────────────
CREATE TABLE audit_logs (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(120) NOT NULL,
    entity_type VARCHAR(80),
    entity_id   UUID,
    ip_address  INET,
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);

-- ── PASSWORD_RESET_TOKENS ─────────────────────────────────────────
CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
