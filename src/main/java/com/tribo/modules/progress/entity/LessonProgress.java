package com.tribo.modules.progress.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Progresso do aluno em cada aula.
 *
 * Atualizada a cada 30 segundos pelo player via POST /progress/lessons/{id}.
 * O Redis serve como buffer — o progresso é gravado no Redis primeiro
 * e persistido no PostgreSQL a cada 60 segundos pelo ProgressFlushJob.
 */
@Entity
@Table(name = "lesson_progress",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "lesson_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** Segundos assistidos acumulados */
    @Column(name = "watched_seconds")
    @Builder.Default
    private Integer watchedSeconds = 0;

    @Column(name = "is_completed")
    @Builder.Default
    private Boolean isCompleted = false;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "last_watched_at")
    @Builder.Default
    private OffsetDateTime lastWatchedAt = OffsetDateTime.now();
}
