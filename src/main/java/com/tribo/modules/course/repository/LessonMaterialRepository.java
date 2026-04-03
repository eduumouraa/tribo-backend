package com.tribo.modules.course.repository;

import com.tribo.modules.course.entity.LessonMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LessonMaterialRepository extends JpaRepository<LessonMaterial, UUID> {

    List<LessonMaterial> findByLessonIdOrderBySortOrderAsc(UUID lessonId);

    @Query("SELECT COUNT(m) FROM LessonMaterial m WHERE m.lesson.id = :lessonId")
    int countByLessonId(@Param("lessonId") UUID lessonId);

    /** Conta materiais por aula em lote — evita N+1 no CourseController. */
    @Query("SELECT m.lesson.id, COUNT(m) FROM LessonMaterial m WHERE m.lesson.id IN :lessonIds GROUP BY m.lesson.id")
    List<Object[]> countByLessonIds(@Param("lessonIds") List<UUID> lessonIds);
}
