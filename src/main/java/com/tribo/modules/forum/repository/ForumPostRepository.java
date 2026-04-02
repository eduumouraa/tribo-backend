package com.tribo.modules.forum.repository;

import com.tribo.modules.forum.entity.ForumPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ForumPostRepository extends JpaRepository<ForumPost, UUID> {

    @Query("SELECT p FROM ForumPost p WHERE p.status = 'ACTIVE' ORDER BY p.pinned DESC, p.createdAt DESC")
    Page<ForumPost> findActiveFeed(Pageable pageable);

    @Query("SELECT p FROM ForumPost p WHERE p.status = 'ACTIVE' AND p.courseId = :courseId ORDER BY p.createdAt DESC")
    Page<ForumPost> findByCourseId(@Param("courseId") UUID courseId, Pageable pageable);

    @Query("SELECT p FROM ForumPost p WHERE p.author.id = :authorId AND p.status = 'ACTIVE' ORDER BY p.createdAt DESC")
    List<ForumPost> findByAuthorId(@Param("authorId") UUID authorId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ForumPost p SET p.likesCount = p.likesCount + 1 WHERE p.id = :id")
    void incrementLikesCount(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ForumPost p SET p.likesCount = CASE WHEN p.likesCount > 0 THEN p.likesCount - 1 ELSE 0 END WHERE p.id = :id")
    void decrementLikesCount(@Param("id") UUID id);
}
