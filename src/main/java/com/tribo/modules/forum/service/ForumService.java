package com.tribo.modules.forum.service;

import com.tribo.modules.forum.dto.ForumDTOs.*;
import com.tribo.modules.forum.entity.CommentLike;
import com.tribo.modules.forum.entity.ForumComment;
import com.tribo.modules.forum.entity.ForumPost;
import com.tribo.modules.forum.entity.PostLike;
import com.tribo.modules.forum.repository.*;
import com.tribo.modules.user.entity.User;
import com.tribo.shared.exception.BusinessException;
import com.tribo.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForumService {

    private final ForumPostRepository postRepository;
    private final ForumCommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;

    // ── Posts ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PostResponse> getFeed(int page, int size, User currentUser) {
        Page<ForumPost> posts = postRepository.findActiveFeed(PageRequest.of(page, size));
        if (posts.isEmpty()) return posts.map(p -> toPostResponse(p, false, 0));

        List<UUID> postIds = posts.map(ForumPost::getId).toList();
        UUID userId = currentUser.getId();

        // Batch: which posts has the viewer liked
        Set<UUID> likedPostIds = postLikeRepository.findLikedPostIdsByUserId(userId, postIds);

        // Batch: comment counts per post (avoids LAZY load of post.getComments())
        Map<UUID, Long> commentCounts = commentRepository.countActiveByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]
                ));

        return posts.map(p -> toPostResponse(
                p,
                likedPostIds.contains(p.getId()),
                commentCounts.getOrDefault(p.getId(), 0L).intValue()
        ));
    }

    @Transactional(readOnly = true)
    public PostDetailResponse getPost(UUID postId, User currentUser) {
        ForumPost post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado."));
        if (!"ACTIVE".equals(post.getStatus())) {
            throw new ResourceNotFoundException("Post não encontrado.");
        }
        List<ForumComment> comments = commentRepository.findByPostId(postId);
        return toPostDetail(post, comments, currentUser.getId());
    }

    @Transactional
    public PostResponse createPost(CreatePostRequest request, User author) {
        ForumPost post = ForumPost.builder()
                .author(author)
                .courseId(request.courseId())
                .title(request.title())
                .body(request.body())
                .build();
        postRepository.save(post);
        log.info("Post criado por userId={}: {}", author.getId(), post.getId());
        return toPostResponse(post, false, 0);
    }

    @Transactional
    public void deletePost(UUID postId, User currentUser) {
        ForumPost post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado."));
        boolean isAuthor = post.getAuthor().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == User.Role.ADMIN || currentUser.getRole() == User.Role.OWNER;
        if (!isAuthor && !isAdmin) {
            throw new BusinessException("Sem permissão para excluir este post.");
        }
        post.setStatus("DELETED");
        postRepository.save(post);
    }

    // ── Comentários ───────────────────────────────────────────────

    @Transactional
    public CommentResponse addComment(UUID postId, CreateCommentRequest request, User author) {
        ForumPost post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado."));
        if (!"ACTIVE".equals(post.getStatus())) {
            throw new BusinessException("Não é possível comentar neste post.");
        }

        ForumComment comment = ForumComment.builder()
                .post(post)
                .author(author)
                .parentId(request.parentId())
                .body(request.body())
                .build();
        commentRepository.save(comment);
        log.info("Comentário adicionado por userId={} no post={}", author.getId(), postId);
        return toCommentResponse(comment, author.getId(), Set.of(), List.of());
    }

    @Transactional
    public void deleteComment(UUID commentId, User currentUser) {
        ForumComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentário não encontrado."));
        boolean isAuthor = comment.getAuthor().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == User.Role.ADMIN || currentUser.getRole() == User.Role.OWNER;
        if (!isAuthor && !isAdmin) {
            throw new BusinessException("Sem permissão para excluir este comentário.");
        }
        comment.setStatus("DELETED");
        commentRepository.save(comment);
    }

    // ── Likes ─────────────────────────────────────────────────────

    @Transactional
    public LikeResponse togglePostLike(UUID postId, User currentUser) {
        ForumPost post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post não encontrado."));
        UUID userId = currentUser.getId();
        boolean alreadyLiked = postLikeRepository.existsByUserIdAndPostId(userId, postId);
        if (alreadyLiked) {
            postLikeRepository.deleteByUserIdAndPostId(userId, postId);
            postRepository.decrementLikesCount(postId);
            return new LikeResponse(false, Math.max(0, post.getLikesCount() - 1));
        } else {
            try {
                postLikeRepository.saveAndFlush(new PostLike(userId, postId, null));
            } catch (DataIntegrityViolationException e) {
                // Concurrent request already inserted the like
                return new LikeResponse(true, post.getLikesCount());
            }
            postRepository.incrementLikesCount(postId);
            return new LikeResponse(true, post.getLikesCount() + 1);
        }
    }

    @Transactional
    public LikeResponse toggleCommentLike(UUID commentId, User currentUser) {
        ForumComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentário não encontrado."));
        UUID userId = currentUser.getId();
        boolean alreadyLiked = commentLikeRepository.existsByUserIdAndCommentId(userId, commentId);
        if (alreadyLiked) {
            commentLikeRepository.deleteByUserIdAndCommentId(userId, commentId);
            commentRepository.decrementLikesCount(commentId);
            return new LikeResponse(false, Math.max(0, comment.getLikesCount() - 1));
        } else {
            try {
                commentLikeRepository.saveAndFlush(new CommentLike(userId, commentId, null));
            } catch (DataIntegrityViolationException e) {
                return new LikeResponse(true, comment.getLikesCount());
            }
            commentRepository.incrementLikesCount(commentId);
            return new LikeResponse(true, comment.getLikesCount() + 1);
        }
    }

    // ── Posts do perfil ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PostResponse> getPostsByUser(UUID authorId, User currentUser) {
        List<ForumPost> posts = postRepository.findByAuthorId(authorId);
        if (posts.isEmpty()) return List.of();

        List<UUID> postIds = posts.stream().map(ForumPost::getId).toList();
        Set<UUID> likedPostIds = postLikeRepository.findLikedPostIdsByUserId(currentUser.getId(), postIds);
        Map<UUID, Long> commentCounts = commentRepository.countActiveByPostIds(postIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        return posts.stream()
                .map(p -> toPostResponse(p, likedPostIds.contains(p.getId()),
                        commentCounts.getOrDefault(p.getId(), 0L).intValue()))
                .toList();
    }

    // ── Mappers ───────────────────────────────────────────────────

    private PostResponse toPostResponse(ForumPost post, boolean liked, int commentsCount) {
        return new PostResponse(
                post.getId().toString(),
                toAuthor(post.getAuthor()),
                post.getCourseId() != null ? post.getCourseId().toString() : null,
                post.getTitle(),
                post.getBody(),
                post.getLikesCount(),
                liked,
                commentsCount,
                post.isPinned(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private PostDetailResponse toPostDetail(ForumPost post, List<ForumComment> allComments, UUID viewerId) {
        boolean liked = postLikeRepository.existsByUserIdAndPostId(viewerId, post.getId());

        // Batch: all comment IDs the viewer liked — one query instead of N
        Set<UUID> commentIds = allComments.stream().map(ForumComment::getId).collect(Collectors.toSet());
        Set<UUID> likedCommentIds = commentIds.isEmpty()
                ? Set.of()
                : commentLikeRepository.findLikedCommentIdsByUserId(viewerId, commentIds);

        // Build tree: roots first, then attach replies
        List<ForumComment> roots = allComments.stream()
                .filter(c -> c.getParentId() == null)
                .toList();

        List<CommentResponse> commentResponses = roots.stream().map(root -> {
            List<CommentResponse> replies = allComments.stream()
                    .filter(c -> root.getId().equals(c.getParentId()))
                    .map(r -> toCommentResponse(r, viewerId, likedCommentIds, List.of()))
                    .toList();
            return toCommentResponse(root, viewerId, likedCommentIds, replies);
        }).toList();

        return new PostDetailResponse(
                post.getId().toString(),
                toAuthor(post.getAuthor()),
                post.getCourseId() != null ? post.getCourseId().toString() : null,
                post.getTitle(),
                post.getBody(),
                post.getLikesCount(),
                liked,
                post.isPinned(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                commentResponses
        );
    }

    private CommentResponse toCommentResponse(
            ForumComment comment, UUID viewerId, Set<UUID> likedIds, List<CommentResponse> replies) {
        return new CommentResponse(
                comment.getId().toString(),
                toAuthor(comment.getAuthor()),
                comment.getParentId() != null ? comment.getParentId().toString() : null,
                comment.getBody(),
                comment.getLikesCount(),
                likedIds.contains(comment.getId()),
                comment.getCreatedAt(),
                replies
        );
    }

    private AuthorResponse toAuthor(User user) {
        return new AuthorResponse(
                user.getId().toString(),
                user.getName(),
                user.getAvatarUrl(),
                user.getRole().name()
        );
    }
}
