package com.tribo.modules.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ForumDTOs {

    // ── Requests ─────────────────────────────────────────────────

    public record CreatePostRequest(
            @NotBlank @Size(min = 3, max = 255) String title,
            @NotBlank @Size(min = 5, max = 5000) String body,
            UUID courseId  // opcional — vincula o post a um curso
    ) {}

    public record CreateCommentRequest(
            @NotBlank @Size(min = 2, max = 2000) String body,
            UUID parentId  // opcional — resposta a outro comentário
    ) {}

    // ── Responses ────────────────────────────────────────────────

    public record AuthorResponse(
            String id,
            String name,
            String avatarUrl,
            String role
    ) {}

    public record CommentResponse(
            String id,
            AuthorResponse author,
            String parentId,
            String body,
            int likesCount,
            boolean likedByMe,
            OffsetDateTime createdAt,
            List<CommentResponse> replies
    ) {}

    public record PostResponse(
            String id,
            AuthorResponse author,
            String courseId,
            String title,
            String body,
            int likesCount,
            boolean likedByMe,
            int commentsCount,
            boolean pinned,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record PostDetailResponse(
            String id,
            AuthorResponse author,
            String courseId,
            String title,
            String body,
            int likesCount,
            boolean likedByMe,
            boolean pinned,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<CommentResponse> comments
    ) {}

    public record LikeResponse(boolean liked, int likesCount) {}
}
