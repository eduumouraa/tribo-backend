package com.tribo.modules.progress.controller;

import com.tribo.modules.progress.service.ProgressService;
import com.tribo.modules.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller de progresso do aluno.
 *
 * GET  /api/v1/progress/me              → progresso geral
 * GET  /api/v1/progress/me/continue     → "Continue Assistindo"
 * POST /api/v1/progress/lessons/{id}    → atualizar tempo assistido
 * PATCH /api/v1/progress/lessons/{id}/complete → marcar/desmarcar como concluída
 */
@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
@Tag(name = "Progresso", description = "Acompanhamento do progresso do aluno")
public class ProgressController {

    private final ProgressService progressService;

    @Operation(summary = "Progresso completo do aluno em todos os cursos")
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyProgress(
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(progressService.getMyProgress(currentUser.getId()));
    }

    @Operation(summary = "Aulas em andamento para 'Continue Assistindo'")
    @GetMapping("/me/continue")
    public ResponseEntity<?> getContinueWatching(
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(progressService.getContinueWatching(currentUser.getId()));
    }

    @Operation(summary = "Atualizar tempo assistido de uma aula (chamado a cada 30s pelo player)")
    @PostMapping("/lessons/{lessonId}")
    public ResponseEntity<Void> updateWatchTime(
            @PathVariable UUID lessonId,
            @RequestBody WatchTimeRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        progressService.updateWatchTime(currentUser.getId(), lessonId, request.watchedSeconds());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Marcar ou desmarcar aula como concluída")
    @PatchMapping("/lessons/{lessonId}/complete")
    public ResponseEntity<Void> toggleComplete(
            @PathVariable UUID lessonId,
            @AuthenticationPrincipal User currentUser
    ) {
        progressService.toggleComplete(currentUser.getId(), lessonId);
        return ResponseEntity.ok().build();
    }

    // ── Request DTO ──────────────────────────────────────────────

    public record WatchTimeRequest(
            @Min(0) int watchedSeconds
    ) {}
}
