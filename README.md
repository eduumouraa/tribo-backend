# Tribo Invest Play — Backend

API REST em Spring Boot 3.3 + Java 21 para a plataforma de streaming educacional.

## Stack

| Camada | Tecnologia |
|--------|-----------|
| Framework | Spring Boot 3.3 |
| Linguagem | Java 21 (Virtual Threads) |
| Banco | PostgreSQL 16 |
| Cache | Redis 7 |
| Autenticação | JWT (JJWT 0.12) |
| Migrations | Flyway |
| Pagamento | Stripe |
| Vídeo | Panda Video → S3 + CloudFront |
| Email | AWS SES |
| Docs | Swagger / OpenAPI |

---

## Pré-requisitos

- Java 21 (recomendamos o [Temurin](https://adoptium.net/))
- Maven 3.9+
- Docker e Docker Compose
- Conta no Stripe (modo test para desenvolvimento)
- Conta no Panda Video

---

## Rodando localmente

### 1. Clone e configure as variáveis

```bash
git clone https://github.com/seu-usuario/tribo-backend.git
cd tribo-backend
cp .env.example .env
# Edite o .env com suas chaves
```

### 2. Sobe o banco e o Redis

```bash
docker compose up -d
# Aguarde o health check passar (10-15 segundos)
```

### 3. Gere o JWT_SECRET

```bash
openssl rand -base64 32
# Copie o resultado para JWT_SECRET no .env
```

### 4. Rode a aplicação

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

A API estará disponível em: `http://localhost:8080`
Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## Endpoints principais

### Autenticação
```
POST /api/v1/auth/register     → Cadastro
POST /api/v1/auth/login        → Login → retorna accessToken + refreshToken
POST /api/v1/auth/refresh      → Renovar access token
POST /api/v1/auth/logout       → Logout (invalida refresh token)
```

### Usuários
```
GET  /api/v1/users/me          → Dados do usuário logado
PUT  /api/v1/users/me          → Atualizar perfil
```

### Cursos
```
GET  /api/v1/courses           → Listar cursos publicados
GET  /api/v1/courses/featured  → Cursos em destaque
GET  /api/v1/courses/{slug}    → Detalhe com módulos e aulas
GET  /api/v1/lessons/{id}/stream → URL de vídeo assinada
```

### Progresso
```
GET   /api/v1/progress/me                    → Progresso geral
GET   /api/v1/progress/me/continue          → Continue Assistindo
POST  /api/v1/progress/lessons/{id}         → Atualizar tempo (30s)
PATCH /api/v1/progress/lessons/{id}/complete → Marcar concluída
```

### Favoritos
```
GET    /api/v1/favorites            → Listar favoritos
POST   /api/v1/favorites/{courseId} → Adicionar
DELETE /api/v1/favorites/{courseId} → Remover
```

### Webhook Stripe
```
POST /api/v1/webhooks/stripe → Recebe eventos do Stripe
```

### Migração Eduzz (OWNER only)
```
POST /api/v1/admin/migration/import → Importar alunos em lote
```

---

## Configurando o Stripe

### 1. Criar produtos no dashboard

Acesse [dashboard.stripe.com](https://dashboard.stripe.com) → Products:

- **Tribo do Investidor** — preço único ou recorrente
- **Organização Financeira** — preço único ou recorrente
- Copie os `prod_xxx` para o `.env`

### 2. Configurar o webhook

1. Stripe Dashboard → Developers → Webhooks → Add endpoint
2. URL: `https://sua-api.com/api/v1/webhooks/stripe`
3. Eventos: `checkout.session.completed`, `customer.subscription.deleted`, `invoice.payment_failed`
4. Copie o Signing secret para `STRIPE_WEBHOOK_SECRET` no `.env`

### 3. Testar localmente com Stripe CLI

```bash
# Instalar: https://stripe.com/docs/stripe-cli
stripe login
stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe
```

---

## Migração da Eduzz

### 1. Exportar alunos

No painel da Eduzz, exporte a lista de alunos em CSV com: nome, email, produto.

### 2. Converter para JSON

```json
{
  "students": [
    { "name": "João Silva", "email": "joao@email.com", "plan": "tribo" },
    { "name": "Ana Lima",   "email": "ana@email.com",  "plan": "combo" }
  ]
}
```

Planos disponíveis: `tribo`, `financas`, `combo`

### 3. Importar

```bash
curl -X POST https://api.triboinvest.com.br/api/v1/admin/migration/import \
  -H "Authorization: Bearer {OWNER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @alunos.json
```

Os alunos são criados com assinatura ativa (provider=eduzz).
Após importar, envie um email para eles com link de reset de senha.

---

## Cadastrando os cursos

Após subir o backend, use o Swagger UI para cadastrar os cursos:

```
POST /api/v1/courses → criar curso
POST /api/v1/modules → criar módulo
POST /api/v1/lessons → criar aula (com video_key do Panda Video)
```

Ou crie diretamente no banco via SQL (mais rápido para a primeira carga):

```sql
INSERT INTO courses (id, title, slug, description, thumbnail_url, category, badge, status, is_featured, sort_order)
VALUES (
  gen_random_uuid(),
  'Tribo do Investidor',
  'tribo-do-investidor',
  'O curso completo da Tribo do Investidor.',
  'https://sua-thumbnail.jpg',
  'Completo',
  'Principal',
  'PUBLISHED',
  true,
  1
);
```

---

## Deploy no Railway

1. Crie um projeto no [Railway](https://railway.app)
2. Adicione PostgreSQL e Redis como plugins
3. Conecte o repositório GitHub
4. Configure as variáveis de ambiente (mesmas do `.env`)
5. O Railway detecta o `pom.xml` e faz o build automaticamente

Variável extra necessária no Railway:
```
SPRING_PROFILES_ACTIVE=prod
```

---

## Estrutura do projeto

```
src/main/java/com/tribo/
├── TriboApplication.java
├── config/
│   └── SecurityConfig.java
├── shared/
│   └── exception/
│       ├── BusinessException.java
│       ├── ResourceNotFoundException.java
│       └── GlobalExceptionHandler.java
└── modules/
    ├── auth/
    │   ├── controller/AuthController.java
    │   ├── service/AuthService.java
    │   ├── service/SubscriptionService.java
    │   ├── dto/AuthDTOs.java
    │   └── security/
    │       ├── JwtService.java
    │       └── JwtAuthFilter.java
    ├── user/
    │   ├── entity/User.java
    │   ├── repository/UserRepository.java
    │   ├── service/UserDetailsServiceImpl.java
    │   └── controller/
    │       ├── UserController.java
    │       └── MigrationController.java
    ├── course/
    │   ├── entity/Course.java, Module.java, Lesson.java
    │   ├── repository/CourseRepository.java, LessonRepository.java
    │   ├── service/CourseService.java, VideoStreamService.java
    │   └── controller/CourseController.java, FavoritesController.java
    ├── progress/
    │   ├── entity/LessonProgress.java
    │   ├── repository/ProgressRepository.java
    │   ├── service/ProgressService.java
    │   └── controller/ProgressController.java
    └── payment/
        ├── entity/Subscription.java
        ├── repository/SubscriptionRepository.java
        └── controller/StripeWebhookController.java
```
