package com.tribo.modules.forum.repository;

import com.tribo.modules.forum.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLike.CommentLikeId> {
    boolean existsByUserIdAndCommentId(UUID userId, UUID commentId);
    void deleteByUserIdAndCommentId(UUID userId, UUID commentId);
}
