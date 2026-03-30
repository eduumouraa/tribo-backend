package com.tribo.modules.user.controller;

import com.tribo.modules.auth.repository.PasswordResetTokenRepository;
import com.tribo.modules.payment.repository.SubscriptionRepository;
import com.tribo.modules.progress.repository.ProgressRepository;
import com.tribo.modules.user.entity.User;
import com.tribo.modules.user.repository.UserRepository;
import com.tribo.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints de dados pessoais — LGPD (Lei 13.709/2018).
 *
 * GET    /api/v1/users/me/data-export  — exportar todos os dados (art. 18, II)
 * DELETE /api/v1/users/me              — excluir conta (art. 18, VI)
 *
 * A exclusão é um soft-delete: anonimiza dados pessoais mas mantém
 * o histórico financeiro de assinaturas para obrigações legais.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "LGPD — Dados Pessoais", description = "Exportação e exclusão de dados conforme LGPD art. 18")
public class UserDataController {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ProgressRepository progressRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Exportação de dados (LGPD art. 18, II) ───────────────────

    @Operation(summary = "Exportar todos os dados pessoais do usuário (LGPD)")
    @GetMapping("/data-export")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> exportData(
            @AuthenticationPrincipal User currentUser
    ) {
        Map<String, Object> export = new LinkedHashMap<>();

        // Dados pessoais
        export.put("dadosPessoais", Map.of(
                "id",        currentUser.getId().toString(),
                "nome",      currentUser.getName(),
                "email",     currentUser.getEmail(),
                "avatarUrl", currentUser.getAvatarUrl() != null ? currentUser.getAvatarUrl() : "",
                "role",      currentUser.getRole().name(),
                "status",    currentUser.getStatus().name(),
                "criadoEm",  currentUser.getCreatedAt() != null ? currentUser.getCreatedAt().toString() : "",
                "ultimoLogin", currentUser.getLastLoginAt() != null ? currentUser.getLastLoginAt().toString() : ""
        ));

        // Histórico de assinaturas
        var assinaturas = subscriptionRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(currentUser.getId()))
                .map(s -> Map.of(
                        "plano",     s.getPlan(),
                        "status",    s.getStatus().name(),
                        "provider",  s.getProvider(),
                        "inicio",    s.getStartedAt() != null ? s.getStartedAt().toString() : "",
                        "expiracao", s.getExpiresAt() != null ? s.getExpiresAt().toString() : "vitalicia",
                        "canceladoEm", s.getCancelledAt() != null ? s.getCancelledAt().toString() : "",
                        "criadoEm",  s.getCreatedAt().toString()
                ))
                .toList();
        export.put("assinaturas", assinaturas);

        // Progresso nos cursos
        var progresso = progressRepository.findByUserId(currentUser.getId()).stream()
                .map(p -> Map.of(
                        "aulaId",         p.getLessonId().toString(),
                        "cursoId",        p.getCourseId().toString(),
                        "segundosAssistidos", p.getWatchedSeconds(),
                        "concluida",      p.getIsCompleted(),
                        "ultimaVez",      p.getLastWatchedAt() != null ? p.getLastWatchedAt().toString() : ""
                ))
                .toList();
        export.put("progresso", progresso);

        export.put("exportadoEm", OffsetDateTime.now().toString());
        export.put("baseLegal",   "LGPD art. 18, inciso II — Direito de acesso aos dados");

        log.info("Exportação de dados solicitada por userId={}", currentUser.getId());
        return ResponseEntity.ok(export);
    }

    // ── Exclusão de conta (LGPD art. 18, VI) ─────────────────────

    @Operation(summary = "Excluir conta permanentemente (LGPD) — ação irreversível")
    @DeleteMapping
    @Transactional
    public ResponseEntity<Map<String, String>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        // Confirmação de senha obrigatória para evitar exclusão acidental
        if (!passwordEncoder.matches(request.password(), currentUser.getPasswordHash())) {
            throw new BusinessException("Senha incorreta. Confirme sua senha para excluir a conta.");
        }

        UUID userId = currentUser.getId();

        // 1. Invalida tokens de reset de senha
        passwordResetTokenRepository.deleteByUserId(userId);

        // 2. Anonimiza dados pessoais (mantém histórico financeiro para obrigações legais)
        String anonEmail = "removido_" + userId.toString().substring(0, 8) + "@excluido.triboinvest.com.br";
        currentUser.setName("Conta Removida");
        currentUser.setEmail(anonEmail);
        currentUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        currentUser.setAvatarUrl(null);
        currentUser.setStatus(User.AccountStatus.SUSPENDED);
        currentUser.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(currentUser);

        // 3. Ao mudar o email, todos os JWTs existentes param de funcionar (session revocation implícita)

        log.info("Conta anonimizada por solicitação LGPD: userId={}", userId);

        return ResponseEntity.ok(Map.of(
                "message",   "Conta excluída com sucesso. Seus dados pessoais foram removidos.",
                "baseLegal", "LGPD art. 18, inciso VI — Direito de exclusão"
        ));
    }

    // ── DTOs ─────────────────────────────────────────────────────

    public record DeleteAccountRequest(
            @NotBlank String password
    ) {}
}
