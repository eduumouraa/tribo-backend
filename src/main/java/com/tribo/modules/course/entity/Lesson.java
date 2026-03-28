package com.tribo.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lessons")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lesson implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Chave do vídeo no Panda Video ou S3.
     * Para Panda Video: é o video_id retornado pela API deles.
     * O LessonService usa essa chave para gerar a URL assinada.
     */
    @Column(name = "video_key")
    private String videoKey;

    @Column(name = "video_provider", length = 20)
    @Builder.Default
    private String videoProvider = "panda";   // panda | s3

    @Column(name = "duration_secs")
    @Builder.Default
    private Integer durationSecs = 0;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_preview")
    @Builder.Default
    private Boolean isPreview = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LessonStatus status = LessonStatus.DRAFT;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum LessonStatus {
        DRAFT, PUBLISHED
    }
}
