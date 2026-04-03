package com.tribo.modules.ranking.service;

import com.tribo.modules.ranking.entity.MemberPoints;
import com.tribo.modules.ranking.repository.MemberPointsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serviço de pontuação de gamificação.
 *
 * Pontuação por ação:
 *   LESSON_COMPLETE  → 10 pts
 *   COURSE_COMPLETE  → 100 pts
 *   FORUM_POST       → 5 pts
 *   FORUM_COMMENT    → 2 pts
 *   ACHIEVEMENT      → 50 pts
 *
 * Executado de forma assíncrona para não bloquear o fluxo principal.
 * Idempotente: um refId só gera pontos uma vez por usuário+reason.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PointsService {

    private static final int POINTS_LESSON_COMPLETE  = 10;
    private static final int POINTS_COURSE_COMPLETE  = 100;
    private static final int POINTS_FORUM_POST       = 5;
    private static final int POINTS_FORUM_COMMENT    = 2;
    private static final int POINTS_ACHIEVEMENT      = 50;

    private final MemberPointsRepository pointsRepository;

    @Async
    @Transactional
    public void awardLessonComplete(UUID userId, UUID lessonId) {
        award(userId, POINTS_LESSON_COMPLETE, "LESSON_COMPLETE", lessonId);
    }

    @Async
    @Transactional
    public void awardCourseComplete(UUID userId, UUID courseId) {
        award(userId, POINTS_COURSE_COMPLETE, "COURSE_COMPLETE", courseId);
    }

    @Async
    @Transactional
    public void awardForumPost(UUID userId, UUID postId) {
        award(userId, POINTS_FORUM_POST, "FORUM_POST", postId);
    }

    @Async
    @Transactional
    public void awardForumComment(UUID userId, UUID commentId) {
        award(userId, POINTS_FORUM_COMMENT, "FORUM_COMMENT", commentId);
    }

    @Async
    @Transactional
    public void awardAchievement(UUID userId, String achievementType) {
        UUID refId = UUID.nameUUIDFromBytes(achievementType.getBytes());
        award(userId, POINTS_ACHIEVEMENT, "ACHIEVEMENT", refId);
    }

    private void award(UUID userId, int points, String reason, UUID refId) {
        if (refId != null && pointsRepository.existsByUserIdAndReasonAndRefId(userId, reason, refId)) {
            return; // já premiado — idempotência
        }
        pointsRepository.save(MemberPoints.builder()
                .userId(userId)
                .points(points)
                .reason(reason)
                .refId(refId)
                .build());
        log.debug("Pontos concedidos: userId={}, reason={}, pts={}", userId, reason, points);
    }
}
