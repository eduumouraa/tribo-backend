package com.tribo.modules.forum.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "comment_likes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(CommentLike.CommentLikeId.class)
public class CommentLike {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "comment_id")
    private UUID commentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class CommentLikeId implements Serializable {
        private UUID userId;
        private UUID commentId;
    }
}
