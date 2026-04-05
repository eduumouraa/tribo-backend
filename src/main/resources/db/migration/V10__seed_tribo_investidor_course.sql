-- ══════════════════════════════════════════════════════════════════
-- V10__seed_tribo_investidor_course.sql
-- Curso: Tribo do Investidor — módulos e aulas
-- Aulas com [em gravação] → status DRAFT
-- Demais → status PUBLISHED
-- ══════════════════════════════════════════════════════════════════

DO $$
DECLARE
    v_course_id     UUID := gen_random_uuid();

    -- módulos
    m_vip           UUID := gen_random_uuid();
    m_intro         UUID := gen_random_uuid();
    m_redes         UUID := gen_random_uuid();
    m_empreend      UUID := gen_random_uuid();
    m_financas      UUID := gen_random_uuid();
    m_exterior      UUID := gen_random_uuid();
    m_cripto        UUID := gen_random_uuid();
    m_consorcio     UUID := gen_random_uuid();
    m_marketplace   UUID := gen_random_uuid();
    m_leiloes       UUID := gen_random_uuid();
    m_acoes         UUID := gen_random_uuid();
    m_renda_fixa    UUID := gen_random_uuid();
    m_bonus         UUID := gen_random_uuid();

BEGIN

-- ── Curso ───────────────────────────────────────────────────────
INSERT INTO courses (id, title, slug, description, status, required_plan, is_featured, sort_order, created_at)
VALUES (
    v_course_id,
    'Tribo do Investidor',
    'tribo-do-investidor',
    'O curso completo da Tribo do Investidor. Aprenda sobre finanças, investimentos, empreendedorismo, leilões, renda fixa, ações e muito mais.',
    'PUBLISHED',
    'tribo',
    true,
    1,
    now()
);

-- ── Módulos ─────────────────────────────────────────────────────

INSERT INTO modules (id, course_id, title, sort_order, created_at) VALUES
    (m_vip,         v_course_id, 'Grupo de Alunos VIP - Tribo do Investidor',                          1,  now()),
    (m_intro,       v_course_id, 'Introdução Geral - Comece por aqui!!',                               2,  now()),
    (m_redes,       v_course_id, 'Posicionamento nas Redes',                                           3,  now()),
    (m_empreend,    v_course_id, 'Empreendedorismo',                                                   4,  now()),
    (m_financas,    v_course_id, 'Finanças - Educação Financeira e Mentalidade',                       5,  now()),
    (m_exterior,    v_course_id, 'Investindo no Exterior',                                             6,  now()),
    (m_cripto,      v_course_id, 'Criptomoedas',                                                       7,  now()),
    (m_consorcio,   v_course_id, 'Consórcio na Prática',                                               8,  now()),
    (m_marketplace, v_course_id, 'Marketplace',                                                        9,  now()),
    (m_leiloes,     v_course_id, 'Leilões na Prática: Do Zero à Primeira Arrematação',                 10, now()),
    (m_acoes,       v_course_id, 'Ações e Fundos Imobiliários na Prática – Do Zero ao Primeiro Investimento', 11, now()),
    (m_renda_fixa,  v_course_id, 'Renda Fixa',                                                        12, now()),
    (m_bonus,       v_course_id, 'Bônus - Tribo do Investidor',                                       13, now());

-- ── Aulas ───────────────────────────────────────────────────────

-- VIP
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_vip, 'Parabéns pela sua decisão - Entre no grupo VIP', 1, 'PUBLISHED', now());

-- Introdução
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_intro, 'Comece por aqui', 1, 'PUBLISHED', now());

-- Posicionamento nas Redes
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_redes, 'Aula 1 – Construindo sua Marca Pessoal',                          1, 'PUBLISHED', now()),
    (gen_random_uuid(), m_redes, 'Aula 2 – Estratégias de Conteúdo e Engajamento - parte 01',      2, 'PUBLISHED', now()),
    (gen_random_uuid(), m_redes, 'Aula 3 - Estratégia de Conteúdo e Engajamento - parte 02',       3, 'PUBLISHED', now()),
    (gen_random_uuid(), m_redes, 'Aula 4 - Estratégia de Conteúdo e Engajamento - parte 03',       4, 'PUBLISHED', now());

-- Empreendedorismo [em gravação]
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_empreend, 'Aula 1 – Mentalidade e Oportunidades de Negócio', 1, 'DRAFT', now()),
    (gen_random_uuid(), m_empreend, 'Aula 2 – Estruturação e Crescimento',             2, 'DRAFT', now());

-- Finanças
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_financas, 'Aula 1 – Introdução - Educação Financeira',                            1,  'PUBLISHED', now()),
    (gen_random_uuid(), m_financas, 'Aula 2 – Conceitos básicos sobre Finanças',                            2,  'PUBLISHED', now()),
    (gen_random_uuid(), m_financas, 'Aula 3 - Aprendendo de forma simples sobre negociação de dívidas',     3,  'PUBLISHED', now()),
    (gen_random_uuid(), m_financas, 'Aula 4 - Deixe os ciclos ruins para prosperar',                        4,  'PUBLISHED', now()),
    (gen_random_uuid(), m_financas, 'Aula 5 - Como eu trato o meu dinheiro?',                               5,  'PUBLISHED', now()),
    (gen_random_uuid(), m_financas, 'Aula 6 - Inteligência Emocional, uma ferramenta para a prosperidade',  6,  'PUBLISHED', now()),
    (gen_random_uuid(), m_financas, 'Aula 7 - Aprenda sobre as dívidas e como evitá-las',                   7,  'PUBLISHED', now()),
    (gen_random_uuid(), m_financas, 'Aula 8 - Introdução aos conceitos de Dívidas',                         8,  'PUBLISHED', now()),
    (gen_random_uuid(), m_financas, 'Aula 9 - As principais causas de endividamento',                       9,  'PUBLISHED', now()),
    (gen_random_uuid(), m_financas, 'Aula 10 - Causas do endividamento e como sair delas',                  10, 'PUBLISHED', now());

-- Investindo no Exterior [em gravação]
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_exterior, 'Aula 1 - Do zero ao primeiro investimento internacional', 1, 'DRAFT', now());

-- Criptomoedas [em gravação]
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_cripto, 'Aula 1 - Fundamentos, riscos e oportunidades no mercado cripto', 1, 'DRAFT', now());

-- Consórcio [em gravação]
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_consorcio, 'Aula 1 - O que você precisa saber e como utilizar o consórcio de forma estratégica', 1, 'DRAFT', now());

-- Marketplace [em gravação]
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_marketplace, 'Aula 1 - Aprenda a vender online e escalar suas vendas no ambiente digital', 1, 'DRAFT', now());

-- Leilões
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_leiloes, 'Aula 1 – Introdução ao Mundo dos Leilões - parte 01', 1, 'PUBLISHED', now()),
    (gen_random_uuid(), m_leiloes, 'Aula 2 – Introdução ao Mundo dos Leilões - parte 02', 2, 'PUBLISHED', now()),
    (gen_random_uuid(), m_leiloes, 'Aula 3 – Introdução ao Mundo dos Leilões - parte 03', 3, 'PUBLISHED', now()),
    (gen_random_uuid(), m_leiloes, 'Aula 4 – Introdução ao Mundo dos Leilões - parte 04', 4, 'PUBLISHED', now()),
    (gen_random_uuid(), m_leiloes, 'Aula 5 – Introdução ao Mundo dos Leilões - parte 05', 5, 'PUBLISHED', now()),
    (gen_random_uuid(), m_leiloes, 'Aula 6 – Como funciona os leilões - Parte 01',        6, 'PUBLISHED', now()),
    (gen_random_uuid(), m_leiloes, 'Aula 7 – Como funciona os leilões - Parte 02',        7, 'PUBLISHED', now()),
    (gen_random_uuid(), m_leiloes, 'Aula 8 – Como funciona os leilões - Parte 03',        8, 'PUBLISHED', now()),
    (gen_random_uuid(), m_leiloes, 'Aula 9 – Como funciona os leilões - Parte 04',        9, 'PUBLISHED', now());

-- Ações e FIIs
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_acoes, 'Aula 1 – Apresentação',                                            1, 'PUBLISHED', now()),
    (gen_random_uuid(), m_acoes, 'Aula 2 – Planejamento - Parte 01',                                 2, 'PUBLISHED', now()),
    (gen_random_uuid(), m_acoes, 'Aula 3 – Planejamento - Parte 02',                                 3, 'PUBLISHED', now()),
    (gen_random_uuid(), m_acoes, 'Aula 4 - Setores das Empresas - Riscos e Benefícios',              4, 'PUBLISHED', now()),
    (gen_random_uuid(), m_acoes, 'Aula 5 - Setores das Empresas - Riscos e Benefícios - Parte 02',  5, 'PUBLISHED', now()),
    (gen_random_uuid(), m_acoes, 'Aula 6 - Indicadores de Mercado',                                  6, 'PUBLISHED', now()),
    (gen_random_uuid(), m_acoes, 'Aula 7 - Indicadores de Mercado - Parte 02',                       7, 'PUBLISHED', now()),
    (gen_random_uuid(), m_acoes, 'Aula 8 - Dividendos',                                              8, 'PUBLISHED', now());

-- Renda Fixa
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_renda_fixa, 'Aula 01 - Tesouro Direto - Parte 01', 1, 'PUBLISHED', now()),
    (gen_random_uuid(), m_renda_fixa, 'Aula 02 - Tesouro Direto - Parte 02', 2, 'PUBLISHED', now()),
    (gen_random_uuid(), m_renda_fixa, 'Aula 03 - CDB - Parte 01',            3, 'PUBLISHED', now()),
    (gen_random_uuid(), m_renda_fixa, 'Aula 04 - CDB - Parte 02',            4, 'PUBLISHED', now()),
    (gen_random_uuid(), m_renda_fixa, 'Aula 05 - Crédito Privado',           5, 'PUBLISHED', now()),
    (gen_random_uuid(), m_renda_fixa, 'Aula 06 - Riscos do Crédito Privado', 6, 'PUBLISHED', now());

-- Bônus
INSERT INTO lessons (id, module_id, title, sort_order, status, created_at) VALUES
    (gen_random_uuid(), m_bonus, 'Planilha completa de ativos e ações',                                          1,  'PUBLISHED', now()),
    (gen_random_uuid(), m_bonus, 'Aula 01 - Bônus - Planejamento Anual Financeiro e Estratégico',               2,  'PUBLISHED', now()),
    (gen_random_uuid(), m_bonus, 'Aula 02 - Bônus - Introdução à Renda Fixa - Princípios e Noções Básicas',    3,  'PUBLISHED', now()),
    (gen_random_uuid(), m_bonus, 'Aula 03 - Bônus - Renda Fixa, Métodos e Aplicações',                          4,  'PUBLISHED', now()),
    (gen_random_uuid(), m_bonus, 'Aula 04 - Bônus - Introdução à Renda Variável',                               5,  'PUBLISHED', now()),
    (gen_random_uuid(), m_bonus, 'Aula 05 - Bônus - Renda Variável na Prática',                                 6,  'PUBLISHED', now()),
    (gen_random_uuid(), m_bonus, 'Aula 06 - Bônus - Renda Variável na Prática (análises de ativos)',            7,  'PUBLISHED', now()),
    (gen_random_uuid(), m_bonus, 'Aula 07 - Bônus - Renda Variável Riscos e Oportunidades',                     8,  'PUBLISHED', now()),
    (gen_random_uuid(), m_bonus, 'Aula 08 - Análise de Ativos e Fundos Imobiliários',                           9,  'PUBLISHED', now()),
    (gen_random_uuid(), m_bonus, 'Aula 09 - Leilão de Imóveis',                                                 10, 'PUBLISHED', now());

END $$;
