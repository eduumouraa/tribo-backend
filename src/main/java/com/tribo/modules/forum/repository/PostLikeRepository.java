package com.tribo.modules.forum.repository;

import com.tribo.modules.forum.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLike.PostLikeId> {
    boolean existsByUserIdAndPostId(UUID userId, UUID postId);
    void deleteByUserIdAndPostId(UUID userId, UUID postId);

    @Query("SELECT pl.postId FROM PostLike pl WHERE pl.userId = :userId AND pl.postId IN :postIds")
    Set<UUID> findLikedPostIdsByUserId(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);
}
