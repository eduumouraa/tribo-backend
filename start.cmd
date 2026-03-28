@echo off
docker-compose up -d
timeout /t 5 /nobreak
set DB_URL=jdbc:postgresql://localhost:5432/tribo
set DB_USER=postgres
set DB_PASSWORD=postgres
set REDIS_HOST=localhost
set REDIS_PORT=6379
set REDIS_PASSWORD=
set JWT_SECRET=DLOKDz+nDfIwxsYt1h/o1eDwXIEN7Xd1tUPwNYyBVf0=
set JWT_ACCESS_EXPIRATION=900
set JWT_REFRESH_EXPIRATION=604800
set SAFEVIDEO_TOKEN=
set STRIPE_SECRET_KEY=sk_test_placeholder
set STRIPE_WEBHOOK_SECRET=whsec_placeholder
set FRONTEND_URL=http://localhost:8080
mvn spring-boot:run