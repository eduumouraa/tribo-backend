package com.tribo.modules.course.repository;

import com.tribo.modules.course.entity.CourseRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseRatingRepository extends JpaRepository<CourseRating, UUID> {

    Optional<CourseRating> findByCourseIdAndUserId(UUID courseId, UUID userId);

    Page<CourseRating> findByCourseIdOrderByCreatedAtDesc(UUID courseId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM CourseRating r WHERE r.courseId = :courseId")
    Double findAverageByCourseId(@Param("courseId") UUID courseId);

    @Query("SELECT COUNT(r) FROM CourseRating r WHERE r.courseId = :courseId")
    int countByCourseId(@Param("courseId") UUID courseId);
}
