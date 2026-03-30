package com.tribo.modules.auth.service;

import com.tribo.modules.auth.dto.AuthDTOs;
import com.tribo.modules.auth.entity.PasswordResetToken;
import com.tribo.modules.auth.repository.PasswordResetTokenRepository;
import com.tribo.modules.auth.security.JwtService;
import com.tribo.modules.notification.service.EmailService;
import com.tribo.modules.user.entity.User;
import com.tribo.modules.user.repository.UserRepository;
import com.tribo.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService — testes unitários")
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock SubscriptionService subscriptionService;
    @Mock EmailService emailService;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .name("João Silva")
                .email("joao@test.com")
                .passwordHash("$2a$12$hashed")
                .role(User.Role.STUDENT)
                .status(User.AccountStatus.ACTIVE)
                .build();

        // User.id is @GeneratedValue — set it manually so getId() != null in assertions
        var idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(testUser, UUID.randomUUID());

        when(jwtService.generateAccessToken(any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getRefreshExpirationSeconds()).thenReturn(604800L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ── Registro ────────────────────────────────────────────────

    @Test
    @DisplayName("register: deve criar usuário e retornar tokens")
    void register_success() {
        var request = new AuthDTOs.RegisterRequest("João Silva", "joao@test.com", "senha1234");

        when(userRepository.existsByEmail("joao@test.com")).thenReturn(false);
        when(passwordEncoder.encode("senha1234")).thenReturn("$2a$12$hashed");
        when(subscriptionService.hasActiveSubscription(any())).thenReturn(false);
        // Simulate JPA assigning an ID when saving a new transient entity
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            if (u.getId() == null) {
                try {
                    var f = User.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(u, UUID.randomUUID());
                } catch (Exception ignored) {}
            }
            return u;
        });

        var response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.user().email()).isEqualTo("joao@test.com");
        verify(emailService).enviarBoasVindas("joao@test.com", "João Silva");
    }

    @Test
    @DisplayName("register: deve lançar exceção se email já cadastrado")
    void register_emailDuplicado() {
        var request = new AuthDTOs.RegisterRequest("João", "joao@test.com", "senha1234");
        when(userRepository.existsByEmail("joao@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email já cadastrado");
    }

    // ── Login ───────────────────────────────────────────────────

    @Test
    @DisplayName("login: deve autenticar e retornar tokens")
    void login_success() {
        var request = new AuthDTOs.LoginRequest("joao@test.com", "senha1234");

        when(userRepository.findByEmail("joao@test.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any())).thenReturn(testUser);
        when(subscriptionService.hasActiveSubscription(any())).thenReturn(true);

        var response = authService.login(request);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().subscriptionActive()).isTrue();
    }

    @Test
    @DisplayName("login: deve lançar exceção com credenciais inválidas")
    void login_credenciaisInvalidas() {
        var request = new AuthDTOs.LoginRequest("joao@test.com", "senhaErrada");

        doThrow(new BadCredentialsException("bad credentials"))
                .when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("incorretos");
    }

    // ── Forgot Password ─────────────────────────────────────────

    @Test
    @DisplayName("forgotPassword: deve gerar token e enviar email se usuário existe")
    void forgotPassword_usuarioExistente() {
        when(userRepository.findByEmail("joao@test.com")).thenReturn(Optional.of(testUser));
        when(passwordResetTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.forgotPassword("joao@test.com");

        verify(passwordResetTokenRepository).deleteByUserId(testUser.getId());
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).enviarRecuperacaoSenha(eq("joao@test.com"), eq("João Silva"), anyString());
    }

    @Test
    @DisplayName("forgotPassword: não deve revelar se email não existe (security)")
    void forgotPassword_emailInexistente() {
        when(userRepository.findByEmail("naoexiste@test.com")).thenReturn(Optional.empty());

        // Não deve lançar exceção — comportamento silencioso para não revelar emails
        assertThatCode(() -> authService.forgotPassword("naoexiste@test.com"))
                .doesNotThrowAnyException();

        verify(emailService, never()).enviarRecuperacaoSenha(any(), any(), any());
    }

    // ── Reset Password ──────────────────────────────────────────

    @Test
    @DisplayName("resetPassword: deve alterar senha com token válido")
    void resetPassword_success() {
        UUID userId = UUID.randomUUID();
        var token = PasswordResetToken.builder()
                .userId(userId)
                .token("valid-token-123")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .used(false)
                .build();

        when(passwordResetTokenRepository.findByToken("valid-token-123")).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("novaSenha123")).thenReturn("$2a$12$newHash");
        when(userRepository.save(any())).thenReturn(testUser);
        when(passwordResetTokenRepository.save(any())).thenReturn(token);

        assertThatCode(() -> authService.resetPassword("valid-token-123", "novaSenha123"))
                .doesNotThrowAnyException();

        verify(userRepository).save(argThat(u -> u.getPasswordHash().equals("$2a$12$newHash")));
    }

    @Test
    @DisplayName("resetPassword: deve lançar exceção com token expirado")
    void resetPassword_tokenExpirado() {
        var token = PasswordResetToken.builder()
                .token("expired-token")
                .expiresAt(OffsetDateTime.now().minusHours(2))
                .used(false)
                .build();

        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword("expired-token", "novaSenha"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    @DisplayName("resetPassword: deve lançar exceção com token já utilizado")
    void resetPassword_tokenJaUsado() {
        var token = PasswordResetToken.builder()
                .token("used-token")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .used(true)
                .build();

        when(passwordResetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword("used-token", "novaSenha"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já foi utilizado");
    }

    @Test
    @DisplayName("resetPassword: deve lançar exceção com token inválido")
    void resetPassword_tokenInvalido() {
        when(passwordResetTokenRepository.findByToken("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("invalid", "novaSenha"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido");
    }
}
