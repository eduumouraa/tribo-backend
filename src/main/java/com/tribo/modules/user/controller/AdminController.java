package com.tribo.modules.user.controller;

import com.tribo.modules.auth.service.SubscriptionService;
import com.tribo.modules.user.entity.User;
import com.tribo.modules.user.repository.UserRepository;
import com.tribo.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Painel administrativo — gestão de alunos e acessos.
 *
 * Apenas ADMIN e OWNER têm acesso a estes endpoints.
 * Permite liberar acesso manualmente sem precisar do Stripe.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminController {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    @GetMapping("/users")
    public ResponseEntity<Page<UserSummary>> listUsers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<User> users = (search != null && !search.isBlank())
                ? userRepository.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(search, search, pageable)
                : userRepository.findAll(pageable);

        return ResponseEntity.ok(users.map(u -> new UserSummary(
                u.getId().toString(), u.getName(), u.getEmail(),
                u.getRole().name(), u.getStatus().name(),
                u.getCreatedAt().toString()
        )));
    }

    /**
     * Libera acesso manual para um aluno — sem precisar do Stripe.
     * Útil para: migração de alunos, cortesia, influencers, testes.
     */
    @PostMapping("/users/{userId}/grant-access")
    public ResponseEntity<AccessResponse> grantAccess(
            @PathVariable UUID userId,
            @RequestBody GrantAccessRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        subscriptionService.activateManual(userId, request.plan(), request.provider());

        log.info("Acesso liberado manualmente para userId={} por admin", userId);
        return ResponseEntity.ok(new AccessResponse(
                userId.toString(), user.getEmail(),
                "Acesso liberado com sucesso para o plano: " + request.plan()
        ));
    }

    /**
     * Revoga o acesso de um aluno.
     */
    @DeleteMapping("/users/{userId}/revoke-access")
    public ResponseEntity<AccessResponse> revokeAccess(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        subscriptionService.cancel(userId);

        log.info("Acesso revogado para userId={} por admin", userId);
        return ResponseEntity.ok(new AccessResponse(
                userId.toString(), user.getEmail(), "Acesso revogado."
        ));
    }

    /**
     * Altera o role de um usuário (ex: promover a ADMIN).
     */
    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<AccessResponse> changeRole(
            @PathVariable UUID userId,
            @RequestBody ChangeRoleRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        User.Role newRole = User.Role.valueOf(request.role().toUpperCase());
        user.setRole(newRole);
        userRepository.save(user);

        log.info("Role alterado para userId={} → {}", userId, newRole);
        return ResponseEntity.ok(new AccessResponse(
                userId.toString(), user.getEmail(),
                "Role alterado para: " + newRole
        ));
    }

    // ── DTOs ─────────────────────────────────────────────────────

    public record UserSummary(
            String id, String name, String email,
            String role, String status, String createdAt
    ) {}

    public record GrantAccessRequest(
            String plan,    // "monthly", "annual", "lifetime"
            String provider // "manual", "eduzz", "hotmart", "courtesy"
    ) {}

    public record ChangeRoleRequest(String role) {}

    public record AccessResponse(String userId, String email, String message) {}
}
