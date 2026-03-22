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

import java.util.concurrent.TimeUnit;

/**
 * Serviço de autenticação.
 *
 * Responsabilidades:
 * - Registrar novos usuários
 * - Autenticar e gerar tokens
 * - Renovar access token via refresh token
 * - Logout (blacklist no Redis)
 * - Reset de senha por email
 */
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

    /**
     * Registra um novo usuário com role STUDENT e status PENDING.
     * Após registro, o usuário precisa confirmar o email para ativar a conta.
     *
     * @throws BusinessException se o email já estiver cadastrado
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Verifica se email já existe
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email já cadastrado. Faça login ou recupere sua senha.");
        }

        // Cria o usuário com senha criptografada
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(User.Role.STUDENT)
                .status(User.AccountStatus.ACTIVE) // em prod: PENDING até confirmar email
                .build();

        userRepository.save(user);
        log.info("Novo usuário registrado: {}", user.getEmail());

        // TODO: enviar email de confirmação via MailService

        return buildAuthResponse(user);
    }

    /**
     * Autentica o usuário e retorna access + refresh tokens.
     * O Spring Security verifica a senha automaticamente via AuthenticationManager.
     *
     * @throws BadCredentialsException se email/senha incorretos
     */
    public AuthResponse login(LoginRequest request) {
        // Lança BadCredentialsException se credenciais inválidas
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException("Usuário não encontrado."));

        log.info("Login realizado: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    /**
     * Renova o access token usando o refresh token.
     * Valida que o token é do tipo "refresh" e não está na blacklist.
     */
    public AuthResponse refresh(String refreshToken) {
        // Verifica blacklist
        if (Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:token:" + refreshToken))) {
            throw new BusinessException("Refresh token inválido. Faça login novamente.");
        }

        // Verifica que é um refresh token (não um access token)
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

    /**
     * Invalida o refresh token adicionando-o à blacklist do Redis.
     * O access token expira naturalmente em 15 minutos.
     */
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            long expiration = jwtService.getRefreshExpirationSeconds();
            redisTemplate.opsForValue().set(
                    "blacklist:token:" + refreshToken,
                    "revoked",
                    expiration,
                    TimeUnit.SECONDS
            );
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        boolean hasActiveSubscription = subscriptionService.hasActiveSubscription(user.getId());

        String accessToken = jwtService.generateAccessToken(user, user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken,
                UserResponseMapper.from(user, hasActiveSubscription)
        );
    }
}
