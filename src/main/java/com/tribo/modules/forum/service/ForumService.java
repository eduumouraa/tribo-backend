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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
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
        return posts.map(p -> toPostResponse(p, currentUser.getId()));
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
        return toPostResponse(post, author.getId());
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
        return toCommentResponse(comment, author.getId(), List.of());
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
            post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
        } else {
            PostLike like = new PostLike(userId, postId, null);
            postLikeRepository.save(like);
            post.setLikesCount(post.getLikesCount() + 1);
        }
        postRepository.save(post);
        return new LikeResponse(!alreadyLiked, post.getLikesCount());
    }

    @Transactional
    public LikeResponse toggleCommentLike(UUID commentId, User currentUser) {
        ForumComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentário não encontrado."));
        UUID userId = currentUser.getId();
        boolean alreadyLiked = commentLikeRepository.existsByUserIdAndCommentId(userId, commentId);
        if (alreadyLiked) {
            commentLikeRepository.deleteByUserIdAndCommentId(userId, commentId);
            comment.setLikesCount(Math.max(0, comment.getLikesCount() - 1));
        } else {
            CommentLike like = new CommentLike(userId, commentId, null);
            commentLikeRepository.save(like);
            comment.setLikesCount(comment.getLikesCount() + 1);
        }
        commentRepository.save(comment);
        return new LikeResponse(!alreadyLiked, comment.getLikesCount());
    }

    // ── Posts do perfil ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PostResponse> getPostsByUser(UUID authorId, User currentUser) {
        return postRepository.findByAuthorId(authorId).stream()
                .map(p -> toPostResponse(p, currentUser.getId()))
                .toList();
    }

    // ── Mappers ───────────────────────────────────────────────────

    private PostResponse toPostResponse(ForumPost post, UUID viewerId) {
        boolean liked = postLikeRepository.existsByUserIdAndPostId(viewerId, post.getId());
        return new PostResponse(
                post.getId().toString(),
                toAuthor(post.getAuthor()),
                post.getCourseId() != null ? post.getCourseId().toString() : null,
                post.getTitle(),
                post.getBody(),
                post.getLikesCount(),
                liked,
                post.getComments().size(),
                post.isPinned(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private PostDetailResponse toPostDetail(ForumPost post, List<ForumComment> allComments, UUID viewerId) {
        boolean liked = postLikeRepository.existsByUserIdAndPostId(viewerId, post.getId());

        // Coleta IDs de comentários que o viewer curtiu
        Set<UUID> likedCommentIds = allComments.stream()
                .filter(c -> commentLikeRepository.existsByUserIdAndCommentId(viewerId, c.getId()))
                .map(ForumComment::getId)
                .collect(Collectors.toSet());

        // Separa raízes de respostas
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
            ForumComment comment, UUID viewerId, List<CommentResponse> replies) {
        boolean liked = commentLikeRepository.existsByUserIdAndCommentId(viewerId, comment.getId());
        return new CommentResponse(
                comment.getId().toString(),
                toAuthor(comment.getAuthor()),
                comment.getParentId() != null ? comment.getParentId().toString() : null,
                comment.getBody(),
                comment.getLikesCount(),
                liked,
                comment.getCreatedAt(),
                replies
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
