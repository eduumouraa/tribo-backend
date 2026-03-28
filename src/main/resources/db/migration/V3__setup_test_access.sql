-- =====================================================================
-- V3__setup_test_access.sql
-- Tribo Invest Play — Configura acesso para testes
-- =====================================================================

-- 1. Atualiza a senha do usuário de teste para "senha123"
--    (hash BCrypt strength 12 da senha "senha123")
UPDATE users
SET
    password_hash = '$2a$12$eImiTXuWVxfM37uY4JANjQ==eImiTXuWVxfM37uY4JANjQ',
    status = 'ACTIVE'
WHERE email = 'luis@triboinvest.com';

-- ATENÇÃO: A hash acima é um placeholder.
-- Para gerar a hash correta, use o endpoint de registro:
-- POST /api/v1/auth/register
-- { "name": "Luis Eduardo", "email": "luis2@triboinvest.com", "password": "senha123" }
-- Depois copie o usuário criado ou use o luis2@triboinvest.com para testes.

-- 2. Libera acesso ao usuário de teste (assinatura manual)
INSERT INTO subscriptions (id, user_id, plan, status, provider, created_at)
SELECT
    gen_random_uuid(),
    u.id,
    'lifetime',
    'ACTIVE',
    'manual',
    now()
FROM users u
WHERE u.email = 'luis@triboinvest.com'
  AND NOT EXISTS (
    SELECT 1 FROM subscriptions s
    WHERE s.user_id = u.id AND s.status = 'ACTIVE'
  );

-- 3. Atualiza as aulas para usar SafeVideo como provider padrão
--    (quando você tiver os video_ids do SafeVideo, atualiza o video_key)
UPDATE lessons SET video_provider = 'safevideo' WHERE video_provider = 'panda';

-- 4. Verifica o resultado
SELECT
    u.name,
    u.email,
    u.role,
    u.status,
    s.plan,
    s.status AS subscription_status,
    s.provider
FROM users u
LEFT JOIN subscriptions s ON s.user_id = u.id AND s.status = 'ACTIVE'
WHERE u.email = 'luis@triboinvest.com';

-- =====================================================================
-- Como cadastrar video_key das aulas no SafeVideo:
--
-- 1. Acesse app.safevideo.com.br e faça upload dos vídeos
-- 2. Copie o ID de cada vídeo (formato: abc123...)
-- 3. Execute para cada aula:
--
-- UPDATE lessons
-- SET video_key = 'SEU_VIDEO_ID_AQUI'
-- WHERE title = 'Nome da Aula';
--
-- Ou em lote para todas as aulas de um módulo:
-- UPDATE lessons l
-- SET video_key = 'VIDEO_ID_AQUI'
-- FROM modules m
-- JOIN courses c ON c.id = m.course_id
-- WHERE l.module_id = m.id
--   AND c.slug = 'tribo-do-investidor'
--   AND l.title = 'Nome da Aula';
-- =====================================================================
