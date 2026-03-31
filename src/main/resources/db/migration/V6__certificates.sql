-- ══════════════════════════════════════════════════════════════
-- V6 — Certificados de conclusão de curso
-- ══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS certificates (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id           UUID         NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    user_name           VARCHAR(120) NOT NULL,
    course_title        VARCHAR(200) NOT NULL,
    verification_code   VARCHAR(30)  NOT NULL UNIQUE,
    issued_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE(user_id, course_id)
);

CREATE INDEX IF NOT EXISTS idx_certificates_user_id ON certificates(user_id);
CREATE INDEX IF NOT EXISTS idx_certificates_code    ON certificates(verification_code);
