package com.tribo.modules.auth.service;

import com.tribo.modules.auth.dto.AuthDTOs.*;
import com.tribo.modules.auth.dto.AuthDTOs.UserResponseMapper;
import com.tribo.modules.auth.security.JwtService;
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

        // Salva o último login
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

    private boolean hasSubscriptionSafely(java.util.UUID userId) {
        try {
            return subscriptionService.hasActiveSubscription(userId);
        } catch (Exception e) {
            log.warn("Erro ao verificar assinatura para userId={}: {}", userId, e.getMessage());
            return false;
        }
    }
}
