package com.tribo.modules.forum.repository;

import com.tribo.modules.forum.entity.ForumComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumCommentRepository extends JpaRepository<ForumComment, UUID> {

    @Query("SELECT c FROM ForumComment c WHERE c.post.id = :postId AND c.status = 'ACTIVE' ORDER BY c.createdAt ASC")
    List<ForumComment> findByPostId(@Param("postId") UUID postId);

    @Query("SELECT c FROM ForumComment c WHERE c.author.id = :authorId AND c.status = 'ACTIVE' ORDER BY c.createdAt DESC")
    List<ForumComment> findByAuthorId(@Param("authorId") UUID authorId);

    @Query("SELECT c.post.id, COUNT(c) FROM ForumComment c WHERE c.post.id IN :postIds AND c.status = 'ACTIVE' GROUP BY c.post.id")
    List<Object[]> countActiveByPostIds(@Param("postIds") Collection<UUID> postIds);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ForumComment c SET c.likesCount = c.likesCount + 1 WHERE c.id = :id")
    void incrementLikesCount(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ForumComment c SET c.likesCount = CASE WHEN c.likesCount > 0 THEN c.likesCount - 1 ELSE 0 END WHERE c.id = :id")
    void decrementLikesCount(@Param("id") UUID id);
}
