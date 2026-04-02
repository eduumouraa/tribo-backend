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
import java.util.Map;

/**
 * Rate limiting por IP para endpoints sensíveis, usando Redis como contador.
 *
 * Endpoints e limites:
 * - POST /api/v1/auth/login           → 10 tentativas / 1 min  (brute force)
 * - POST /api/v1/auth/register        → 5 registros  / 10 min  (spam de contas)
 * - POST /api/v1/auth/forgot-password → 5 tentativas / 10 min  (flood de emails)
 *
 * Estratégia: sliding window simplificada com Redis INCR + EXPIRE.
 * Resiliente: se Redis estiver fora, permite a requisição (fail-open).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, String> redisTemplate;

    private record RateLimitRule(int maxAttempts, Duration window, Duration blockDuration) {}

    private static final Map<String, RateLimitRule> RULES = Map.of(
        "/api/v1/auth/login",            new RateLimitRule(10, Duration.ofMinutes(1),  Duration.ofMinutes(5)),
        "/api/v1/auth/register",         new RateLimitRule(5,  Duration.ofMinutes(10), Duration.ofMinutes(30)),
        "/api/v1/auth/forgot-password",  new RateLimitRule(5,  Duration.ofMinutes(10), Duration.ofMinutes(30))
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitRule rule = RULES.get(request.getRequestURI());
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = extractIp(request);
        String slug     = request.getRequestURI().replace("/", ":");
        String blockKey = "ratelimit:blocked:" + slug + ":" + ip;
        String countKey = "ratelimit:count:"   + slug + ":" + ip;

        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
                Long ttl = redisTemplate.getExpire(blockKey);
                String msg = "Muitas tentativas. Aguarde " + formatTtl(ttl != null ? ttl : rule.blockDuration().getSeconds()) + ".";
                writeRateLimitResponse(response, msg);
                log.warn("Rate limit ativo — IP={}, endpoint={}", ip, request.getRequestURI());
                return;
            }

            Long attempts = redisTemplate.opsForValue().increment(countKey);
            if (attempts != null && attempts == 1) {
                redisTemplate.expire(countKey, rule.window());
            }

            if (attempts != null && attempts > rule.maxAttempts()) {
                redisTemplate.opsForValue().set(blockKey, "blocked", rule.blockDuration());
                redisTemplate.delete(countKey);
                String msg = "Muitas tentativas. Aguarde " + formatTtl(rule.blockDuration().getSeconds()) + ".";
                writeRateLimitResponse(response, msg);
                log.warn("Rate limit ativado — IP={}, endpoint={}, tentativas={}", ip, request.getRequestURI(), attempts);
                return;
            }

        } catch (Exception e) {
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

    private String formatTtl(long seconds) {
        if (seconds >= 60) return (seconds / 60) + " minuto(s)";
        return seconds + " segundo(s)";
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
