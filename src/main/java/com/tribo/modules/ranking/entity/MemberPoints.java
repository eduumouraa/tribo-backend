package com.tribo.modules.ranking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "member_points")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPoints {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Integer points;

    /**
     * Motivo da pontuação:
     * LESSON_COMPLETE | COURSE_COMPLETE | FORUM_POST | FORUM_COMMENT | ACHIEVEMENT | MANUAL
     */
    @Column(nullable = false, length = 100)
    private String reason;

    /** ID da aula/curso/post relacionado (opcional). */
    @Column(name = "ref_id")
    private UUID refId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
