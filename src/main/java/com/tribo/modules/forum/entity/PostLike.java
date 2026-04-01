package com.tribo.modules.forum.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "post_likes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(PostLike.PostLikeId.class)
public class PostLike {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "post_id")
    private UUID postId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PostLikeId implements Serializable {
        private UUID userId;
        private UUID postId;
    }
}
