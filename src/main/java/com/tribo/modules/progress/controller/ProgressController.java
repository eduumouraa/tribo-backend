package com.tribo.modules.progress.controller;

import com.tribo.modules.achievement.service.AchievementService;
import com.tribo.modules.certificate.service.CertificateService;
import com.tribo.modules.progress.entity.LessonProgress;
import com.tribo.modules.progress.repository.ProgressRepository;
import com.tribo.modules.progress.service.ProgressService;
import com.tribo.modules.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller de progresso do aluno.
 *
 * GET  /api/v1/progress/me                     → progresso geral por curso
 * GET  /api/v1/progress/me/lessons              → progresso por AULA (usado pelo frontend)
 * GET  /api/v1/progress/me/continue             → "Continue Assistindo"
 * GET  /api/v1/progress/me/courses/{courseId}   → progresso de um curso específico
 * POST /api/v1/progress/lessons/{id}            → atualizar tempo assistido
 * PATCH /api/v1/progress/lessons/{id}/complete  → marcar/desmarcar como concluída
 */
@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
@Tag(name = "Progresso", description = "Acompanhamento do progresso do aluno")
public class ProgressController {

    private final ProgressService progressService;
    private final ProgressRepository progressRepository;
    private final AchievementService achievementService;
    private final CertificateService certificateService;

    @Operation(summary = "Progresso geral do aluno em todos os cursos")
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyProgress(
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(progressService.getMyProgress(currentUser.getId()));
    }

    /**
     * Retorna o progresso por AULA no formato que o frontend espera.
     *
     * Formato: {
     *   "courseId1": {
     *     "lessonId1": { "completed": true, "watchedSeconds": 480, "lastWatchedAt": 123456789 },
     *     "lessonId2": { "completed": false, "watchedSeconds": 120, "lastWatchedAt": 123456789 }
     *   }
     * }
     */
    @Operation(summary = "Progresso por aula — formato otimizado para o frontend")
    @GetMapping("/me/lessons")
    public ResponseEntity<Map<String, Map<String, Object>>> getMyLessonsProgress(
            @AuthenticationPrincipal User currentUser
    ) {
        List<LessonProgress> allProgress = progressRepository.findByUserId(currentUser.getId());

        Map<String, Map<String, Object>> result = new HashMap<>();

        for (LessonProgress p : allProgress) {
            String courseId = p.getCourseId().toString();
            String lessonId = p.getLessonId().toString();

            result.computeIfAbsent(courseId, k -> new HashMap<>());

            Map<String, Object> lessonData = new HashMap<>();
            lessonData.put("completed", p.getIsCompleted());
            lessonData.put("watchedSeconds", p.getWatchedSeconds());
            lessonData.put("lastWatchedAt", p.getLastWatchedAt() != null
                    ? p.getLastWatchedAt().toInstant().toEpochMilli() : null);

            result.get(courseId).put(lessonId, lessonData);
        }

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Progresso de um curso específico")
    @GetMapping("/me/courses/{courseId}")
    public ResponseEntity<Map<String, Object>> getCourseProgress(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal User currentUser
    ) {
        List<LessonProgress> lessons = progressRepository
                .findByUserIdAndCourseId(currentUser.getId(), courseId);

        Map<String, Object> lessonMap = new HashMap<>();
        for (LessonProgress p : lessons) {
            Map<String, Object> lessonData = new HashMap<>();
            lessonData.put("completed", p.getIsCompleted());
            lessonData.put("watchedSeconds", p.getWatchedSeconds());
            lessonData.put("lastWatchedAt", p.getLastWatchedAt() != null
                    ? p.getLastWatchedAt().toInstant().toEpochMilli() : null);
            lessonMap.put(p.getLessonId().toString(), lessonData);
        }

        long completed = lessons.stream().filter(LessonProgress::getIsCompleted).count();
        Long totalWatched = lessons.stream()
                .mapToLong(LessonProgress::getWatchedSeconds).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("courseId", courseId.toString());
        result.put("completedLessons", completed);
        result.put("totalWatchedSeconds", totalWatched);
        result.put("lessons", lessonMap);

        return ResponseEntity.ok(result);
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
        LessonProgress progress = progressService.toggleComplete(currentUser.getId(), lessonId);
        // Dispara conquistas e certificado apenas quando a aula é marcada como concluída
        if (Boolean.TRUE.equals(progress.getIsCompleted())) {
            achievementService.checkAndAward(currentUser.getId(), progress.getCourseId());
            certificateService.generateIfCompleted(currentUser.getId(), progress.getCourseId());
        }
        return ResponseEntity.ok().build();
    }

    public record WatchTimeRequest(@Min(0) int watchedSeconds) {}
}
