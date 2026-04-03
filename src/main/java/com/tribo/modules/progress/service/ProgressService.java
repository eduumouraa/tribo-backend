package com.tribo.modules.progress.service;

import com.tribo.modules.achievement.service.AchievementService;
import com.tribo.modules.course.entity.Course;
import com.tribo.modules.course.entity.Lesson;
import com.tribo.modules.course.repository.CourseRepository;
import com.tribo.modules.course.repository.LessonRepository;
import com.tribo.modules.progress.entity.LessonProgress;
import com.tribo.modules.progress.repository.ProgressRepository;
import com.tribo.modules.ranking.service.PointsService;
import com.tribo.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço de progresso do aluno.
 *
 * Endpoints chamados com alta frequência:
 * - POST /progress/lessons/{id}        → a cada 30s pelo player
 * - PATCH /progress/lessons/{id}/complete → quando aula termina
 * - GET /progress/me                   → ao carregar o dashboard
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressService {

    private final ProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final PointsService pointsService;
    private final AchievementService achievementService;

    /**
     * Atualiza o tempo assistido de uma aula.
     * Cria o registro de progresso se não existir (upsert).
     */
    @Transactional
    public void updateWatchTime(UUID userId, UUID lessonId, int watchedSeconds) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Aula não encontrada."));

        UUID courseId = lesson.getModule().getCourse().getId();

        LessonProgress progress = progressRepository
                .findByUserIdAndLessonId(userId, lessonId)
                .orElseGet(() -> LessonProgress.builder()
                        .userId(userId)
                        .lessonId(lessonId)
                        .courseId(courseId)
                        .build());

        // Só atualiza se o novo valor for maior (evita regressão)
        if (watchedSeconds > progress.getWatchedSeconds()) {
            progress.setWatchedSeconds(watchedSeconds);
        }
        progress.setLastWatchedAt(OffsetDateTime.now());
        progressRepository.save(progress);
    }

    /**
     * Marca uma aula como concluída (toggle).
     * Se já estiver concluída, desmarca.
     */
    @Transactional
    public LessonProgress toggleComplete(UUID userId, UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Aula não encontrada."));

        UUID courseId = lesson.getModule().getCourse().getId();

        LessonProgress progress = progressRepository
                .findByUserIdAndLessonId(userId, lessonId)
                .orElseGet(() -> LessonProgress.builder()
                        .userId(userId)
                        .lessonId(lessonId)
                        .courseId(courseId)
                        .build());

        boolean wasCompleted = progress.getIsCompleted();
        boolean nowCompleted = !wasCompleted;
        progress.setIsCompleted(nowCompleted);
        progress.setCompletedAt(nowCompleted ? OffsetDateTime.now() : null);
        progress.setLastWatchedAt(OffsetDateTime.now());

        LessonProgress saved = progressRepository.save(progress);

        // Gamificação: concede pontos e verifica conquistas ao concluir (não ao desmarcar)
        if (nowCompleted) {
            pointsService.awardLessonComplete(userId, lessonId);
            achievementService.checkAndAward(userId, courseId);

            // Verifica se completou o curso inteiro
            int totalLessons = courseRepository.countPublishedLessons(courseId);
            long completedInCourse = progressRepository.countByUserIdAndCourseIdAndIsCompleted(userId, courseId, true);
            if (totalLessons > 0 && completedInCourse >= totalLessons) {
                pointsService.awardCourseComplete(userId, courseId);
            }
        }

        return saved;
    }

    /**
     * Retorna o progresso consolidado de todos os cursos do aluno.
     * Formato esperado pelo frontend: { courses: [...], totalWatchedSeconds: N }
     */
    public Map<String, Object> getMyProgress(UUID userId) {
        List<LessonProgress> allProgress = progressRepository.findByUserId(userId);
        Long totalWatchedSeconds = progressRepository.sumWatchedSeconds(userId);

        // Agrupa por curso
        Map<UUID, List<LessonProgress>> byCourse = allProgress.stream()
                .collect(Collectors.groupingBy(LessonProgress::getCourseId));

        List<Map<String, Object>> courseProgress = new ArrayList<>();

        for (Map.Entry<UUID, List<LessonProgress>> entry : byCourse.entrySet()) {
            UUID courseId = entry.getKey();
            List<LessonProgress> lessonProgressList = entry.getValue();

            // Busca o total de aulas do curso para calcular percentual
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course == null) continue;

            int totalLessons = course.getLessonsCount();
            long completedLessons = lessonProgressList.stream()
                    .filter(LessonProgress::getIsCompleted)
                    .count();

            int percent = totalLessons > 0
                    ? (int) Math.round((completedLessons * 100.0) / totalLessons)
                    : 0;

            // Última aula assistida
            LessonProgress lastWatched = lessonProgressList.stream()
                    .max(Comparator.comparing(LessonProgress::getLastWatchedAt))
                    .orElse(null);

            Map<String, Object> cp = new LinkedHashMap<>();
            cp.put("courseId", courseId.toString());
            cp.put("completedLessons", completedLessons);
            cp.put("totalLessons", totalLessons);
            cp.put("percentComplete", percent);
            cp.put("lastWatchedAt", lastWatched != null ? lastWatched.getLastWatchedAt() : null);
            cp.put("lastLessonId", lastWatched != null ? lastWatched.getLessonId().toString() : null);
            courseProgress.add(cp);
        }

        return Map.of(
                "courses", courseProgress,
                "totalWatchedSeconds", totalWatchedSeconds != null ? totalWatchedSeconds : 0
        );
    }

    /**
     * Retorna aulas em andamento para a seção "Continue Assistindo".
     */
    public List<LessonProgress> getContinueWatching(UUID userId) {
        return progressRepository.findContinueWatching(userId);
    }
}
