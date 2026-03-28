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

    @Query("SELECT c FROM Course c WHERE c.status = com.tribo.modules.course.entity.Course.CourseStatus.PUBLISHED ORDER BY c.sortOrder ASC")
    Page<Course> findPublished(Pageable pageable);

    @Query("SELECT c FROM Course c WHERE c.status = 'PUBLISHED' AND c.isFeatured = true ORDER BY c.sortOrder ASC")
    List<Course> findFeatured();

    @Query("SELECT DISTINCT c FROM Course c LEFT JOIN FETCH c.modules m LEFT JOIN FETCH m.lessons l WHERE c.slug = :slug")
    Optional<Course> findBySlugWithModulesAndLessons(@Param("slug") String slug);
}