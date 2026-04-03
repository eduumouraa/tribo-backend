package com.tribo.modules.achievement.service;

import com.tribo.modules.achievement.entity.Achievement;
import com.tribo.modules.achievement.repository.AchievementRepository;
import com.tribo.modules.course.repository.CourseRepository;
import com.tribo.modules.notification.service.NotificationService;
import com.tribo.modules.progress.repository.ProgressRepository;
import com.tribo.modules.ranking.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verifica e concede conquistas após cada aula concluída.
 * Executado de forma assíncrona para não impactar a resposta do progresso.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final ProgressRepository progressRepository;
    private final CourseRepository courseRepository;
    private final NotificationService notificationService;
    private final PointsService pointsService;

    @Async
    @Transactional
    public void checkAndAward(UUID userId, UUID courseId) {
        long completedTotal = progressRepository.countByUserIdAndIsCompleted(userId, true);

        award(userId, "PRIMEIRA_AULA", completedTotal >= 1,
                "Você completou sua primeira aula!", Map.of());

        long watchedSeconds = progressRepository.sumWatchedSecondsByUserId(userId);
        award(userId, "MARATONISTA", watchedSeconds >= 18_000,
                "Você assistiu 5 horas de conteúdo!", Map.of("horasAssistidas", watchedSeconds / 3600));

        award(userId, "DEDICADO", watchedSeconds >= 72_000,
                "Você assistiu 20 horas de conteúdo!", Map.of("horasAssistidas", watchedSeconds / 3600));

        checkCourseComplete(userId, courseId);
    }

    private void checkCourseComplete(UUID userId, UUID courseId) {
        int totalLessons = courseRepository.countPublishedLessons(courseId);
        if (totalLessons == 0) return;

        long completedInCourse = progressRepository.countByUserIdAndCourseIdAndIsCompleted(userId, courseId, true);
        if (completedInCourse < totalLessons) return;

        award(userId, "CURSO_COMPLETO", true,
                "Você completou um curso inteiro!", Map.of("courseId", courseId.toString()));

        courseRepository.findById(courseId).ifPresent(course -> {
            String slug = course.getSlug();
            if ("tribo-do-investidor".equals(slug)) {
                award(userId, "TRIBO_COMPLETA", true,
                        "Você completou o Tribo do Investidor!", Map.of("courseId", courseId.toString()));
            } else if (slug != null && slug.contains("organizacao-financeira")) {
                award(userId, "FINANCAS_COMPLETA", true,
                        "Você completou o curso de Organização Financeira!", Map.of("courseId", courseId.toString()));
            }
        });
    }

    private void award(UUID userId, String type, boolean condition, String message, Map<String, Object> metadata) {
        if (!condition) return;
        if (achievementRepository.existsByUserIdAndType(userId, type)) return;

        Achievement achievement = Achievement.builder()
                .userId(userId)
                .type(type)
                .metadata(metadata)
                .build();
        achievementRepository.save(achievement);

        notificationService.create(userId, "achievement",
                "Conquista desbloqueada!", message,
                Map.of("achievementType", type));

        pointsService.awardAchievement(userId, type);

        log.info("Conquista {} concedida para userId={}", type, userId);
    }

    @Transactional(readOnly = true)
    public List<Achievement> listByUser(UUID userId) {
        return achievementRepository.findByUserIdOrderByEarnedAtDesc(userId);
    }
}
