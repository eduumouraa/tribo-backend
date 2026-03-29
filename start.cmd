@echo off
title Tribo Invest Play - Backend
color 0A

echo.
echo  =========================================
echo   TRIBO INVEST PLAY - Backend
echo  =========================================
echo.

REM Sobe o Docker (PostgreSQL + Redis)
echo [1/3] Subindo banco e Redis...
docker-compose up -d
timeout /t 5 /nobreak > nul
echo       OK!

REM Variaveis de ambiente
echo [2/3] Configurando variaveis...
set DB_URL=jdbc:postgresql://localhost:5432/tribo
set DB_USER=postgres
set DB_PASSWORD=postgres
set REDIS_HOST=localhost
set REDIS_PORT=6379
set REDIS_PASSWORD=
set RESEND_API_KEY=re_jh6Wkow5_AnU4ibGLzRYsikUdUFmNcjGp
set JWT_SECRET=DLOKDz+nDfIwxsYt1h/o1eDwXIEN7Xd1tUPwNYyBVf0=
set JWT_ACCESS_EXPIRATION=900
set JWT_REFRESH_EXPIRATION=604800
set SAFEVIDEO_TOKEN=
set STRIPE_SECRET_KEY=sk_test_placeholder
set STRIPE_WEBHOOK_SECRET=whsec_pRgvHC5Mr0SJhUGFoHGF1ImW9d1FME8x
set FRONTEND_URL=http://localhost:8080
echo       OK!

REM Sobe o backend
echo [3/3] Iniciando backend...
echo.
echo  Acesse: http://localhost:8081/api/v1/courses
echo  Admin:  http://localhost:8081/api/v1/admin/dashboard
echo.
mvn spring-boot:run
