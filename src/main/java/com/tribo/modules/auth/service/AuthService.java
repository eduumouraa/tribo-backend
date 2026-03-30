package com.tribo.modules.auth.service;

import com.tribo.modules.auth.dto.AuthDTOs.*;
import com.tribo.modules.auth.dto.AuthDTOs.UserResponseMapper;
import com.tribo.modules.auth.entity.PasswordResetToken;
import com.tribo.modules.auth.repository.PasswordResetTokenRepository;
import com.tribo.modules.auth.security.JwtService;
import com.tribo.modules.notification.service.EmailService;
import com.tribo.modules.user.entity.User;
import com.tribo.modules.user.repository.UserRepository;
import com.tribo.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final SubscriptionService subscriptionService;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email já cadastrado. Faça login ou recupere sua senha.");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(User.Role.STUDENT)
                .status(User.AccountStatus.ACTIVE)
                .build();

        userRepository.save(user);
        log.info("Novo usuário registrado: {}", user.getEmail());

        emailService.enviarBoasVindas(user.getEmail(), user.getName());

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException e) {
            throw new BusinessException("Email ou senha incorretos.");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("Usuário não encontrado."));

        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        log.info("Login realizado: {} (role={})", user.getEmail(), user.getRole());
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        if (isTokenBlacklisted(refreshToken)) {
            throw new BusinessException("Refresh token inválido. Faça login novamente.");
        }

        String type = jwtService.extractType(refreshToken);
        if (!"refresh".equals(type)) {
            throw new BusinessException("Token inválido.");
        }

        String email = jwtService.extractEmail(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado."));

        if (jwtService.isTokenExpired(refreshToken)) {
            throw new BusinessException("Sessão expirada. Faça login novamente.");
        }

        return buildAuthResponse(user);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        try {
            long expiration = jwtService.getRefreshExpirationSeconds();
            redisTemplate.opsForValue().set(
                    "blacklist:token:" + refreshToken,
                    "revoked",
                    expiration,
                    TimeUnit.SECONDS
            );
            log.debug("Token adicionado à blacklist do Redis");
        } catch (Exception e) {
            log.warn("Redis indisponível no logout: {}", e.getMessage());
        }
    }

    /**
     * Solicita recuperação de senha.
     * Responde sempre com sucesso para não revelar se o email existe.
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            // Invalida tokens anteriores do usuário
            passwordResetTokenRepository.deleteByUserId(user.getId());

            String token = UUID.randomUUID().toString().replace("-", "");
            passwordResetTokenRepository.save(
                PasswordResetToken.builder()
                    .userId(user.getId())
                    .token(token)
                    .expiresAt(OffsetDateTime.now().plusHours(1))
                    .build()
            );

            emailService.enviarRecuperacaoSenha(user.getEmail(), user.getName(), token);
            log.info("Token de reset de senha gerado para: {}", email);
        });
    }

    /**
     * Redefine a senha usando o token recebido por email.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException("Link inválido ou já utilizado."));

        if (resetToken.getUsed()) {
            throw new BusinessException("Este link já foi utilizado. Solicite um novo.");
        }

        if (resetToken.isExpired()) {
            throw new BusinessException("Link expirado. Solicite um novo link de recuperação.");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new BusinessException("Usuário não encontrado."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        log.info("Senha redefinida com sucesso para userId={}", user.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────

    private boolean isTokenBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:token:" + token));
        } catch (Exception e) {
            log.warn("Redis indisponível ao verificar blacklist: {}", e.getMessage());
            return false;
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        boolean hasActiveSubscription = hasSubscriptionSafely(user.getId());
        if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.OWNER) {
            hasActiveSubscription = true;
        }

        String accessToken = jwtService.generateAccessToken(user, user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken,
                UserResponseMapper.from(user, hasActiveSubscription));
    }

    private boolean hasSubscriptionSafely(UUID userId) {
        try {
            return subscriptionService.hasActiveSubscription(userId);
        } catch (Exception e) {
            log.warn("Erro ao verificar assinatura para userId={}: {}", userId, e.getMessage());
            return false;
        }
    }
}
