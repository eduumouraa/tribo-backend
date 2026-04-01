package com.tribo.modules.forum.repository;

import com.tribo.modules.forum.entity.ForumComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ForumCommentRepository extends JpaRepository<ForumComment, UUID> {

    @Query("SELECT c FROM ForumComment c WHERE c.post.id = :postId AND c.status = 'ACTIVE' ORDER BY c.createdAt ASC")
    List<ForumComment> findByPostId(@Param("postId") UUID postId);

    @Query("SELECT c FROM ForumComment c WHERE c.author.id = :authorId AND c.status = 'ACTIVE' ORDER BY c.createdAt DESC")
    List<ForumComment> findByAuthorId(@Param("authorId") UUID authorId);
}
