package com.tribo.modules.forum.controller;

import com.tribo.modules.forum.dto.ForumDTOs.*;
import com.tribo.modules.forum.service.ForumService;
import com.tribo.modules.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints do fórum/comunidade.
 *
 * GET  /api/v1/forum/posts              — feed paginado
 * POST /api/v1/forum/posts              — criar post
 * GET  /api/v1/forum/posts/:id          — detalhe com comentários
 * DELETE /api/v1/forum/posts/:id        — excluir post (autor ou admin)
 * POST /api/v1/forum/posts/:id/like     — toggle like no post
 * POST /api/v1/forum/posts/:id/comments — adicionar comentário
 * DELETE /api/v1/forum/comments/:id     — excluir comentário
 * POST /api/v1/forum/comments/:id/like  — toggle like no comentário
 * GET  /api/v1/forum/users/:userId/posts — posts de um usuário (perfil)
 */
@RestController
@RequestMapping("/api/v1/forum")
@RequiredArgsConstructor
@Tag(name = "Fórum", description = "Comunidade e discussões dos alunos")
public class ForumController {

    private final ForumService forumService;

    // ── Posts ─────────────────────────────────────────────────────

    @Operation(summary = "Feed de posts paginado")
    @GetMapping("/posts")
    public ResponseEntity<Page<PostResponse>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(forumService.getFeed(page, Math.min(size, 50), currentUser));
    }

    @Operation(summary = "Criar novo post")
    @PostMapping("/posts")
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(forumService.createPost(request, currentUser));
    }

    @Operation(summary = "Detalhe do post com comentários")
    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(forumService.getPost(postId, currentUser));
    }

    @Operation(summary = "Excluir post (autor ou admin)")
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User currentUser
    ) {
        forumService.deletePost(postId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Toggle like no post")
    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<LikeResponse> togglePostLike(
            @PathVariable UUID postId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(forumService.togglePostLike(postId, currentUser));
    }

    // ── Comentários ───────────────────────────────────────────────

    @Operation(summary = "Adicionar comentário ou resposta a um post")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(forumService.addComment(postId, request, currentUser));
    }

    @Operation(summary = "Excluir comentário (autor ou admin)")
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal User currentUser
    ) {
        forumService.deleteComment(commentId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Toggle like em comentário")
    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<LikeResponse> toggleCommentLike(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(forumService.toggleCommentLike(commentId, currentUser));
    }

    // ── Perfil ────────────────────────────────────────────────────

    @Operation(summary = "Posts de um usuário (para exibir no perfil)")
    @GetMapping("/users/{userId}/posts")
    public ResponseEntity<List<PostResponse>> getUserPosts(
            @PathVariable UUID userId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(forumService.getPostsByUser(userId, currentUser));
    }
}
