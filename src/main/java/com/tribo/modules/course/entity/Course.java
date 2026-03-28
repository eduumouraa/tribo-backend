package com.tribo.modules.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entidade de Curso.
 * Implements Serializable para que o Redis consiga serializar o cache.
 */
@Entity
@Table(name = "courses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    private String category;

    private String badge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CourseStatus status = CourseStatus.DRAFT;

    @Column(name = "is_featured")
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * Metadados flexíveis em JSONB — o que você aprende, pré-requisitos.
     * Estrutura: { "whatYouLearn": [...], "requirements": [...] }
     * Usamos LinkedHashMap (Serializable) para compatibilidade com Redis.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * Módulos do curso — carregados apenas quando necessário (LAZY).
     * ATENÇÃO: Lazy collections NÃO são serializáveis pelo Redis.
     * Por isso removemos o @Cacheable do findBySlug/findPublished no service
     * e usamos DTOs para cache quando necessário.
     */
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<Module> modules = new ArrayList<>();

    // ── Campos calculados ─────────────────────────────────────────

    /** Total de aulas publicadas em todos os módulos */
    @Transient
    public int getLessonsCount() {
        return modules.stream()
                .flatMap(m -> m.getLessons().stream())
                .filter(l -> l.getStatus() == Lesson.LessonStatus.PUBLISHED)
                .mapToInt(l -> 1)
                .sum();
    }

    /** Duração total em segundos de todas as aulas publicadas */
    @Transient
    public int getDurationSecs() {
        return modules.stream()
                .flatMap(m -> m.getLessons().stream())
                .filter(l -> l.getStatus() == Lesson.LessonStatus.PUBLISHED)
                .mapToInt(Lesson::getDurationSecs)
                .sum();
    }

    public enum CourseStatus {
        DRAFT, PUBLISHED, ARCHIVED
    }
}
