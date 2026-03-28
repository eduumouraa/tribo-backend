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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Painel administrativo completo.
 *
 * Apenas ADMIN e OWNER têm acesso.
 *
 * GET  /api/v1/admin/dashboard         → estatísticas gerais
 * GET  /api/v1/admin/users             → lista de alunos
 * GET  /api/v1/admin/users/{id}        → detalhe do aluno
 * POST /api/v1/admin/users/{id}/grant-access  → liberar acesso
 * DELETE /api/v1/admin/users/{id}/revoke-access → revogar acesso
 * PATCH /api/v1/admin/users/{id}/role  → mudar role
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminController {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Dashboard com estatísticas reais do banco.
     * Mostra tudo que está acontecendo na plataforma.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> stats = new HashMap<>();

        // Total de alunos
        long totalUsers = userRepository.count();
        stats.put("totalUsers", totalUsers);

        // Alunos com assinatura ativa
        Long activeSubscriptions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE status = 'ACTIVE'",
                Long.class
        );
        stats.put("activeSubscriptions", activeSubscriptions != null ? activeSubscriptions : 0);

        // Total de cursos publicados
        Long totalCourses = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM courses WHERE status = 'PUBLISHED'",
                Long.class
        );
        stats.put("totalCourses", totalCourses != null ? totalCourses : 0);

        // Total de módulos
        Long totalModules = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM modules",
                Long.class
        );
        stats.put("totalModules", totalModules != null ? totalModules : 0);

        // Total de aulas
        Long totalLessons = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lessons WHERE status = 'PUBLISHED'",
                Long.class
        );
        stats.put("totalLessons", totalLessons != null ? totalLessons : 0);

        // Aulas concluídas (todos os alunos)
        Long totalCompletions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lesson_progress WHERE is_completed = true",
                Long.class
        );
        stats.put("totalCompletions", totalCompletions != null ? totalCompletions : 0);

        // Total de horas assistidas
        Long totalWatchedSeconds = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(watched_seconds), 0) FROM lesson_progress",
                Long.class
        );
        stats.put("totalWatchedHours", totalWatchedSeconds != null ? totalWatchedSeconds / 3600 : 0);

        // Alunos cadastrados nos últimos 30 dias
        Long newUsersThisMonth = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE created_at >= NOW() - INTERVAL '30 days'",
                Long.class
        );
        stats.put("newUsersThisMonth", newUsersThisMonth != null ? newUsersThisMonth : 0);

        // Alunos ativos hoje (assistiram alguma aula hoje)
        Long activeToday = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM lesson_progress WHERE last_watched_at >= NOW() - INTERVAL '24 hours'",
                Long.class
        );
        stats.put("activeToday", activeToday != null ? activeToday : 0);

        // Alunos mais recentes (últimos 5)
        List<Map<String, Object>> recentUsers = jdbcTemplate.queryForList(
                "SELECT id, name, email, role, status, created_at FROM users ORDER BY created_at DESC LIMIT 5"
        );
        stats.put("recentUsers", recentUsers);

        // Cursos mais assistidos
        List<Map<String, Object>> topCourses = jdbcTemplate.queryForList(
                "SELECT c.title, COUNT(DISTINCT lp.user_id) as unique_viewers, " +
                "SUM(lp.watched_seconds) as total_watched_seconds " +
                "FROM courses c " +
                "LEFT JOIN modules m ON m.course_id = c.id " +
                "LEFT JOIN lessons l ON l.module_id = m.id " +
                "LEFT JOIN lesson_progress lp ON lp.lesson_id = l.id " +
                "GROUP BY c.id, c.title " +
                "ORDER BY unique_viewers DESC LIMIT 5"
        );
        stats.put("topCourses", topCourses);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserSummary>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<User> users;
        if (search != null && !search.isBlank()) {
            users = userRepository.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(
                    search, search, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return ResponseEntity.ok(users.map(u -> new UserSummary(
                u.getId().toString(), u.getName(), u.getEmail(),
                u.getRole().name(), u.getStatus().name(),
                u.getCreatedAt() != null ? u.getCreatedAt().toString() : null
        )));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> getUserDetail(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Map<String, Object> detail = new HashMap<>();
        detail.put("id", user.getId().toString());
        detail.put("name", user.getName());
        detail.put("email", user.getEmail());
        detail.put("role", user.getRole().name());
        detail.put("status", user.getStatus().name());
        detail.put("avatarUrl", user.getAvatarUrl());
        detail.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);

        // Assinatura ativa
        boolean hasSubscription = subscriptionService.hasActiveSubscription(userId);
        detail.put("subscriptionActive", hasSubscription);

        // Progresso geral
        List<Map<String, Object>> progress = jdbcTemplate.queryForList(
                "SELECT c.title as course_title, " +
                "COUNT(CASE WHEN lp.is_completed = true THEN 1 END) as completed_lessons, " +
                "COUNT(l.id) as total_lessons, " +
                "COALESCE(SUM(lp.watched_seconds), 0) as watched_seconds " +
                "FROM courses c " +
                "JOIN modules m ON m.course_id = c.id " +
                "JOIN lessons l ON l.module_id = m.id " +
                "LEFT JOIN lesson_progress lp ON lp.lesson_id = l.id AND lp.user_id = ? " +
                "GROUP BY c.id, c.title",
                userId
        );
        detail.put("progress", progress);

        return ResponseEntity.ok(detail);
    }

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

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<AccessResponse> changeStatus(
            @PathVariable UUID userId,
            @RequestBody ChangeStatusRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        User.AccountStatus newStatus = User.AccountStatus.valueOf(request.status().toUpperCase());
        user.setStatus(newStatus);
        userRepository.save(user);

        log.info("Status alterado para userId={} → {}", userId, newStatus);
        return ResponseEntity.ok(new AccessResponse(
                userId.toString(), user.getEmail(),
                "Status alterado para: " + newStatus
        ));
    }

    // ── DTOs ─────────────────────────────────────────────────────

    public record UserSummary(
            String id, String name, String email,
            String role, String status, String createdAt
    ) {}

    public record GrantAccessRequest(
            String plan,     // monthly | annual | lifetime
            String provider  // manual | eduzz | hotmart | courtesy
    ) {}

    public record ChangeRoleRequest(String role) {}

    public record ChangeStatusRequest(String status) {}

    public record AccessResponse(String userId, String email, String message) {}
}
