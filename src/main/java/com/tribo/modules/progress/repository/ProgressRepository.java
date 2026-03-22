package com.tribo.modules.progress.repository;

import com.tribo.modules.progress.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgressRepository extends JpaRepository<LessonProgress, UUID> {

    List<LessonProgress> findByUserId(UUID userId);

    List<LessonProgress> findByUserIdAndCourseId(UUID userId, UUID courseId);

    Optional<LessonProgress> findByUserIdAndLessonId(UUID userId, UUID lessonId);

    /**
     * Retorna os cursos que o aluno está assistindo atualmente,
     * ordenados pelo mais recente — usado na seção "Continue Assistindo".
     */
    @Query("SELECT p FROM LessonProgress p WHERE p.userId = :userId AND p.isCompleted = false ORDER BY p.lastWatchedAt DESC LIMIT 5")
    List<LessonProgress> findContinueWatching(UUID userId);

    /** Total de segundos assistidos pelo usuário em todos os cursos */
    @Query("SELECT COALESCE(SUM(p.watchedSeconds), 0) FROM LessonProgress p WHERE p.userId = :userId")
    Long sumWatchedSeconds(UUID userId);
}
