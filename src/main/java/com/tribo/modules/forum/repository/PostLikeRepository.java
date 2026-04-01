package com.tribo.modules.forum.repository;

import com.tribo.modules.forum.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLike.PostLikeId> {
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);
    void deleteByUserIdAndPostId(UUID userId, UUID postId);
}
