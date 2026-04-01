package com.tribo.modules.auth.controller;

import com.tribo.modules.auth.dto.AuthDTOs.*;
import com.tribo.modules.auth.service.AuthService;
import com.tribo.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Controller de autenticação.
 * Todas as rotas são públicas (configurado no SecurityConfig).
 *
 * Tokens JWT são entregues via cookies httpOnly (não em localStorage):
 *   - access_token  : path=/, maxAge=15min
 *   - refresh_token : path=/api/v1/auth, maxAge=7 dias
 *
 * POST /api/v1/auth/register          — cadastro
 * POST /api/v1/auth/login             — login
 * POST /api/v1/auth/refresh           — renovar access token (via cookie)
 * POST /api/v1/auth/logout            — logout (limpa cookies + blacklist)
 * POST /api/v1/auth/forgot-password   — solicitar recuperação de senha
 * POST /api/v1/auth/reset-password    — redefinir senha com token
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login, registro e gestão de tokens via cookies httpOnly")
public class AuthController {

    private final AuthService authService;

    /**
     * Se true, os cookies são marcados Secure (HTTPS only) e SameSite=None.
     * Em desenvolvimento local (HTTP), setar APP_COOKIE_SECURE=false.
     */
    @Value("${app.cookie-secure:true}")
    private boolean cookieSecure;

    // ── Endpoints ────────────────────────────────────────────────

    @Operation(summary = "Registrar novo usuário")
    @PostMapping("/register")
    public ResponseEntity<UserLoginResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthResponse auth = authService.register(request);
        setAuthCookies(response, auth.accessToken(), auth.refreshToken());
        return ResponseEntity.ok(new UserLoginResponse(auth.user()));
    }

    @Operation(summary = "Login")
    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthResponse auth = authService.login(request);
        setAuthCookies(response, auth.accessToken(), auth.refreshToken());
        return ResponseEntity.ok(new UserLoginResponse(auth.user()));
    }

    @Operation(summary = "Renovar access token via cookie refresh_token")
    @PostMapping("/refresh")
    public ResponseEntity<UserLoginResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractCookieValue(request, "refresh_token");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("Sessão expirada. Faça login novamente.");
        }
        AuthResponse auth = authService.refresh(refreshToken);
        setAuthCookies(response, auth.accessToken(), auth.refreshToken());
        return ResponseEntity.ok(new UserLoginResponse(auth.user()));
    }

    @Operation(summary = "Logout — invalida o refresh token e limpa cookies")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractCookieValue(request, "refresh_token");
        authService.logout(refreshToken);
        clearAuthCookies(response);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Solicitar recuperação de senha — envia link por email")
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(new MessageResponse(
            "Se este email estiver cadastrado, você receberá as instruções em breve."
        ));
    }

    @Operation(summary = "Redefinir senha com token recebido por email")
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse("Senha redefinida com sucesso. Faça login."));
    }

    // ── Cookie helpers ───────────────────────────────────────────

    private void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        String sameSite = cookieSecure ? "None" : "Lax";

        // access_token — 15 min, enviado em todas as rotas
        ResponseCookie accessCookie = ResponseCookie.from("access_token", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(Duration.ofMinutes(15))
                .sameSite(sameSite)
                .build();

        // refresh_token — 7 dias, enviado apenas para /api/v1/auth (refresh + logout)
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(7))
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        String sameSite = cookieSecure ? "None" : "Lax";

        ResponseCookie clearAccess = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite(sameSite)
                .build();

        ResponseCookie clearRefresh = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .sameSite(sameSite)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, clearAccess.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefresh.toString());
    }

    private String extractCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    // ── DTOs locais ───────────────────────────────────────────────

    public record MessageResponse(String message) {}
}
