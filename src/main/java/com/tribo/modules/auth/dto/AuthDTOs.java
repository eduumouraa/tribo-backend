package com.tribo.modules.auth.dto;

import com.tribo.modules.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs de autenticação.
 * Records são ideais para DTOs — imutáveis e sem boilerplate.
 */
public class AuthDTOs {

    // ── Requests ─────────────────────────────────────────────────

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {}

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {}

    // ── Responses ────────────────────────────────────────────────

    /**
     * Resposta do login — o frontend salva o accessToken no localStorage
     * e usa o refreshToken (cookie HttpOnly) para renovar.
     */
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            UserResponse user
    ) {}

    /**
     * Representação segura do usuário — sem dados sensíveis.
     * O campo subscriptionActive é calculado consultando a tabela subscriptions.
     */
    public record UserResponse(
            String id,
            String name,
            String email,
            String role,
            String avatarUrl,
            boolean subscriptionActive
    ) {}

    // Helper class para converter User em UserResponse
    public static class UserResponseMapper {
        public static UserResponse from(com.tribo.modules.user.entity.User user, boolean subscriptionActive) {
            return new UserResponse(
                    user.getId().toString(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole().name(),
                    user.getAvatarUrl(),
                    subscriptionActive
            );
        }
    }
}
