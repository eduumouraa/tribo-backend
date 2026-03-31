package com.tribo.modules.achievement.controller;

import com.tribo.modules.achievement.entity.Achievement;
import com.tribo.modules.achievement.service.AchievementService;
import com.tribo.modules.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
@Tag(name = "Conquistas", description = "Badges e conquistas do aluno")
public class AchievementController {

    private final AchievementService achievementService;

    @Operation(summary = "Listar conquistas do aluno autenticado")
    @GetMapping("/me")
    public ResponseEntity<List<AchievementResponse>> myAchievements(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                achievementService.listByUser(user.getId())
                        .stream().map(this::toResponse).toList()
        );
    }

    private AchievementResponse toResponse(Achievement a) {
        return new AchievementResponse(
                a.getId().toString(),
                a.getType(),
                label(a.getType()),
                description(a.getType()),
                a.getMetadata(),
                a.getEarnedAt()
        );
    }

    private String label(String type) {
        return switch (type) {
            case "PRIMEIRA_AULA"     -> "Primeiro Passo";
            case "MARATONISTA"       -> "Maratonista";
            case "DEDICADO"          -> "Dedicado";
            case "CURSO_COMPLETO"    -> "Conclusão";
            case "TRIBO_COMPLETA"    -> "Tribo Completa";
            case "FINANCAS_COMPLETA" -> "Mestre das Finanças";
            default -> type;
        };
    }

    private String description(String type) {
        return switch (type) {
            case "PRIMEIRA_AULA"     -> "Completou a primeira aula na plataforma";
            case "MARATONISTA"       -> "Assistiu 5 horas de conteúdo";
            case "DEDICADO"          -> "Assistiu 20 horas de conteúdo";
            case "CURSO_COMPLETO"    -> "Completou um curso inteiro";
            case "TRIBO_COMPLETA"    -> "Completou o Tribo do Investidor";
            case "FINANCAS_COMPLETA" -> "Completou o curso de Organização Financeira";
            default -> "";
        };
    }

    public record AchievementResponse(
            String id, String type, String label, String description,
            Map<String, Object> metadata, OffsetDateTime earnedAt
    ) {}
}
