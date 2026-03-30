package com.tribo.modules.auth.controller;

import com.tribo.modules.auth.dto.AuthDTOs.*;
import com.tribo.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de autenticação.
 * Todas as rotas são públicas (configurado no SecurityConfig).
 *
 * POST /api/v1/auth/register          — cadastro
 * POST /api/v1/auth/login             — login
 * POST /api/v1/auth/refresh           — renovar access token
 * POST /api/v1/auth/logout            — logout (invalida refresh token)
 * POST /api/v1/auth/forgot-password   — solicitar recuperação de senha
 * POST /api/v1/auth/reset-password    — redefinir senha com token
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login, registro e gestão de tokens")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Registrar novo usuário")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(summary = "Login")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Renovar access token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Logout — invalida o refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        if (request != null) {
            authService.logout(request.refreshToken());
        }
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Solicitar recuperação de senha — envia link por email")
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        // Sempre retorna sucesso para não revelar se o email existe
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

    public record MessageResponse(String message) {}
}
