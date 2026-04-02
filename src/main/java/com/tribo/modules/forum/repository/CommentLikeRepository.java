package com.tribo.modules.forum.repository;

import com.tribo.modules.forum.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLike.CommentLikeId> {
    boolean existsByUserIdAndCommentId(UUID userId, UUID commentId);
    void deleteByUserIdAndCommentId(UUID userId, UUID commentId);

    @Query("SELECT cl.commentId FROM CommentLike cl WHERE cl.userId = :userId AND cl.commentId IN :commentIds")
    Set<UUID> findLikedCommentIdsByUserId(@Param("userId") UUID userId, @Param("commentIds") Collection<UUID> commentIds);
}
