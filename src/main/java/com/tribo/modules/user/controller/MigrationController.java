package com.tribo.modules.user.controller;

import com.tribo.modules.auth.service.SubscriptionService;
import com.tribo.modules.user.entity.User;
import com.tribo.modules.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller de migração da Eduzz para a nova plataforma.
 *
 * ACESSO RESTRITO: apenas OWNER pode usar estes endpoints.
 *
 * Fluxo de migração:
 * 1. Exportar alunos da Eduzz em CSV (nome, email, produto)
 * 2. Converter para JSON e chamar POST /api/v1/admin/migration/import
 * 3. Os alunos recebem email automático para definir nova senha
 * 4. A assinatura é marcada como provider=eduzz para auditoria
 *
 * Exemplo de payload:
 * {
 *   "students": [
 *     { "name": "João Silva", "email": "joao@email.com", "plan": "tribo" },
 *     { "name": "Ana Lima",   "email": "ana@email.com",  "plan": "combo" }
 *   ]
 * }
 */
@RestController
@RequestMapping("/api/v1/admin/migration")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
@Tag(name = "Migração", description = "Importar alunos da Eduzz (uso único)")
@Slf4j
public class MigrationController {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "Importar alunos da Eduzz em lote")
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importStudents(
            @RequestBody ImportRequest request
    ) {
        List<String> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (StudentData student : request.students()) {
            try {
                if (userRepository.existsByEmail(student.email())) {
                    // Aluno já existe — apenas ativa a assinatura
                    userRepository.findByEmail(student.email()).ifPresent(user -> {
                        user.setStatus(User.AccountStatus.ACTIVE);
                        userRepository.save(user);
                        subscriptionService.activateFromMigration(user.getId(), student.plan());
                    });
                    skipped.add(student.email() + " (já existia — assinatura ativada)");
                } else {
                    // Cria novo usuário
                    User user = User.builder()
                            .name(student.name())
                            .email(student.email())
                            // Senha bloqueada — usuário deve fazer reset
                            .passwordHash(passwordEncoder.encode(
                                    "MIGRATED_" + System.currentTimeMillis()))
                            .role(User.Role.STUDENT)
                            .status(User.AccountStatus.ACTIVE)
                            .build();

                    userRepository.save(user);
                    subscriptionService.activateFromMigration(user.getId(), student.plan());

                    // TODO: enviar email de boas-vindas com link de reset de senha
                    // mailService.sendWelcomeMigrationEmail(user);

                    imported.add(student.email());
                    log.info("Aluno migrado: {} — plano: {}", student.email(), student.plan());
                }
            } catch (Exception e) {
                log.error("Erro ao migrar {}: {}", student.email(), e.getMessage());
                errors.add(student.email() + ": " + e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "imported", imported.size(),
                "skipped", skipped.size(),
                "errors", errors.size(),
                "importedEmails", imported,
                "skippedEmails", skipped,
                "errorDetails", errors
        ));
    }

    // ── DTOs ─────────────────────────────────────────────────────

    public record StudentData(
            String name,
            String email,
            String plan       // tribo | financas | combo
    ) {}

    public record ImportRequest(
            List<StudentData> students
    ) {}
}
