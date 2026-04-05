package com.tribo.modules.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Serviço responsável por gerar, validar e extrair informações de tokens JWT.
 *
 * Fluxo:
 * 1. Login → gera access token (15min) + refresh token (7 dias)
 * 2. Requisição autenticada → JwtAuthFilter valida o access token
 * 3. Access token expirado → /auth/refresh usa o refresh token para gerar novos
 * 4. Logout → refresh token é adicionado à blacklist no Redis
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;      // em segundos (padrão: 900 = 15 min)

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;     // em segundos (padrão: 604800 = 7 dias)

    // ── Geração de tokens ────────────────────────────────────────

    /**
     * Gera o access token JWT com o email do usuário como subject.
     * Inclui o role no payload para que o frontend possa usar sem
     * chamar /users/me a cada requisição.
     */
    public String generateAccessToken(UserDetails userDetails, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("type", "access");
        return buildToken(claims, userDetails.getUsername(), accessExpiration * 1000L);
    }

    /**
     * Gera o refresh token — contém apenas o subject e tipo.
     * Armazenado em cookie HttpOnly no navegador.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return buildToken(claims, userDetails.getUsername(), refreshExpiration * 1000L);
    }

    private String buildToken(Map<String, Object> claims, String subject, long expirationMs) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ── Validação e extração ─────────────────────────────────────

    /**
     * Valida o token: verifica assinatura, expiração e se o subject
     * corresponde ao usuário fornecido.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public String extractType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpiration;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
