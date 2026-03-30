package com.tribo.modules.course.repository;

import com.tribo.modules.course.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    Optional<Course> findBySlug(String slug);

    @Query("SELECT c FROM Course c WHERE c.status = 'PUBLISHED' ORDER BY c.sortOrder ASC")
    Page<Course> findPublished(Pageable pageable);

    @Query("SELECT c FROM Course c WHERE c.status = 'PUBLISHED' AND c.isFeatured = true ORDER BY c.sortOrder ASC")
    List<Course> findFeatured();

    @Query("SELECT DISTINCT c FROM Course c LEFT JOIN FETCH c.modules m LEFT JOIN FETCH m.lessons l WHERE c.slug = :slug")
    Optional<Course> findBySlugWithModulesAndLessons(@Param("slug") String slug);

    /**
     * Conta aulas publicadas de um curso via SQL nativo.
     * Evita LazyInitializationException do @Transient getLessonsCount().
     */
    @Query(value = """
        SELECT COUNT(l.id)
        FROM lessons l
        JOIN modules m ON m.id = l.module_id
        WHERE m.course_id = :courseId
          AND l.status = 'PUBLISHED'
        """, nativeQuery = true)
    int countPublishedLessons(@Param("courseId") UUID courseId);

    /**
     * Soma duração total das aulas publicadas de um curso.
     */
    @Query(value = """
        SELECT COALESCE(SUM(l.duration_secs), 0)
        FROM lessons l
        JOIN modules m ON m.id = l.module_id
        WHERE m.course_id = :courseId
          AND l.status = 'PUBLISHED'
        """, nativeQuery = true)
    int sumPublishedDuration(@Param("courseId") UUID courseId);

    /**
     * Retorna o required_plan do curso a que a aula pertence.
     * Usado para verificar acesso antes de gerar URL de stream.
     */
    @Query(value = """
        SELECT c.required_plan
        FROM courses c
        JOIN modules m ON m.course_id = c.id
        JOIN lessons l ON l.module_id = m.id
        WHERE l.id = :lessonId
        LIMIT 1
        """, nativeQuery = true)
    String findRequiredPlanByLessonId(@Param("lessonId") UUID lessonId);

    /**
     * Retorna o slug do curso a que a aula pertence.
     * Usado para preencher o erro de acesso negado.
     */
    @Query(value = """
        SELECT c.slug
        FROM courses c
        JOIN modules m ON m.course_id = c.id
        JOIN lessons l ON l.module_id = m.id
        WHERE l.id = :lessonId
        LIMIT 1
        """, nativeQuery = true)
    String findSlugByLessonId(@Param("lessonId") UUID lessonId);
}
