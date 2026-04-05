-- ══════════════════════════════════════════════════════════════════
-- V11__seed_organizacao_financeira_course.sql
-- Curso: Organização Financeira e Negociação de Dívidas
-- ══════════════════════════════════════════════════════════════════

DO $$
DECLARE
    v_course_id  UUID := gen_random_uuid();
    m_intro      UUID := gen_random_uuid();
    m_grupo      UUID := gen_random_uuid();
    m_conclusao  UUID := gen_random_uuid();

BEGIN

-- ── Curso ───────────────────────────────────────────────────────
INSERT INTO courses (id, title, slug, description, status, required_plan, is_featured, sort_order, created_at)
VALUES (
    v_course_id,
    'Organização Financeira e Negociação de Dívidas',
    'organizacao-financeira-negociacao-dividas',
    'Aprenda a organizar suas finanças, entender as causas do endividamento e negociar suas dívidas de forma eficaz.',
    'PUBLISHED',
    'financas',
    false,
    2,
    now()
);

-- ── Módulos ─────────────────────────────────────────────────────
INSERT INTO modules (id, course_id, title, sort_order, created_at) VALUES
    (m_intro,     v_course_id, 'Introdução',      1, now()),
    (m_grupo,     v_course_id, 'Grupo Tribo',     2, now()),
    (m_conclusao, v_course_id, 'Módulo 1 - Conclusão', 3, now());

-- ── Aulas ───────────────────────────────────────────────────────

-- Introdução
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_intro, 'Aula 1 - Organização e Negociação',                               1, 'PUBLISHED', now()),
    (gen_random_uuid(), m_intro, 'Aula 2 - Introdução aos conceitos de Dívidas',                    2, 'PUBLISHED', now()),
    (gen_random_uuid(), m_intro, 'Aula 3 - Principais causas do Endividamento',                     3, 'PUBLISHED', now()),
    (gen_random_uuid(), m_intro, 'Aula 4 - Causas do endividamento e como evitar as dívidas',       4, 'PUBLISHED', now()),
    (gen_random_uuid(), m_intro, 'Aula 5 - Vencendo as Dívidas',                                    5, 'PUBLISHED', now());

-- Grupo Tribo
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_grupo, 'Acesse o grupo VIP no WhatsApp', 1, 'PUBLISHED', now());

-- Conclusão
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_conclusao, 'Aula técnica - Como sair dos embaraços das dívidas',  1, 'PUBLISHED', now()),
    (gen_random_uuid(), m_conclusao, 'Aula técnica 2 - Nunca mais erre na negociação',      2, 'PUBLISHED', now());

END $$;
