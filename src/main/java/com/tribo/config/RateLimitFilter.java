package com.tribo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate limiting no endpoint de login usando Redis como contador.
 *
 * Estratégia: sliding window simplificada.
 * - Máximo de 10 tentativas por IP em 1 minuto.
 * - Após exceder: bloqueia por 5 minutos.
 * - Não usa Bucket4j — Redis puro, sem dependência extra.
 *
 * Resiliente: se Redis estiver fora, permite a requisição (fail-open).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(5);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!LOGIN_PATH.equals(request.getRequestURI()) ||
            !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = extractIp(request);
        String blockKey = "ratelimit:blocked:" + ip;
        String countKey = "ratelimit:count:" + ip;

        try {
            // Verifica se IP está bloqueado
            if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
                writeRateLimitResponse(response, "Muitas tentativas de login. Aguarde 5 minutos.");
                log.warn("Login bloqueado por rate limit — IP={}", ip);
                return;
            }

            // Incrementa contador
            Long attempts = redisTemplate.opsForValue().increment(countKey);

            // Define expiração na primeira tentativa
            if (attempts != null && attempts == 1) {
                redisTemplate.expire(countKey, WINDOW);
            }

            // Bloqueia se excedeu o limite
            if (attempts != null && attempts > MAX_ATTEMPTS) {
                redisTemplate.opsForValue().set(blockKey, "blocked", BLOCK_DURATION);
                redisTemplate.delete(countKey);
                writeRateLimitResponse(response, "Muitas tentativas de login. Aguarde 5 minutos.");
                log.warn("Rate limit ativado — IP={}, tentativas={}", ip, attempts);
                return;
            }

        } catch (Exception e) {
            // Redis fora do ar — fail-open para não bloquear o login
            log.warn("Redis indisponível no rate limit — permitindo requisição: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"code":"TOO_MANY_REQUESTS","message":"%s"}
                """.formatted(message));
    }
}
