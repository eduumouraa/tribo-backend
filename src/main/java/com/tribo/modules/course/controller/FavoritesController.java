package com.tribo.modules.course.controller;

import com.tribo.modules.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
@Tag(name = "Favoritos", description = "Gerenciar cursos favoritos")
public class FavoritesController {

    private final FavoritesService favoritesService;

    @Operation(summary = "Listar favoritos do usuário")
    @GetMapping
    public ResponseEntity<List<FavoriteResponse>> list(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(favoritesService.list(currentUser.getId()));
    }

    @Operation(summary = "Adicionar curso aos favoritos")
    @PostMapping("/{courseId}")
    public ResponseEntity<Void> add(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal User currentUser
    ) {
        favoritesService.add(currentUser.getId(), courseId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Remover curso dos favoritos")
    @DeleteMapping("/{courseId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal User currentUser
    ) {
        favoritesService.remove(currentUser.getId(), courseId);
        return ResponseEntity.ok().build();
    }

    public record FavoriteResponse(String courseId) {}
}

// ── Embeddable ID ─────────────────────────────────────────────────────────────

@Embeddable
class FavoriteId implements Serializable {
    @Column(name = "user_id")
    UUID userId;

    @Column(name = "course_id")
    UUID courseId;
}

// ── Entity ────────────────────────────────────────────────────────────────────

@Entity
@Table(name = "course_favorites")
class CourseFavorite {

    @EmbeddedId
    FavoriteId id = new FavoriteId();

    @Column(name = "created_at")
    OffsetDateTime createdAt = OffsetDateTime.now();
}

// ── Repository ────────────────────────────────────────────────────────────────

@Repository
interface FavoriteRepository extends JpaRepository<CourseFavorite, FavoriteId> {

    @Query("SELECT f FROM CourseFavorite f WHERE f.id.userId = :userId")
    List<CourseFavorite> findByUserId(UUID userId);

    void deleteByIdUserIdAndIdCourseId(UUID userId, UUID courseId);

    boolean existsByIdUserIdAndIdCourseId(UUID userId, UUID courseId);
}

// ── Service ───────────────────────────────────────────────────────────────────

@Service
@RequiredArgsConstructor
class FavoritesService {

    private final FavoriteRepository favoriteRepository;

    public List<FavoritesController.FavoriteResponse> list(UUID userId) {
        return favoriteRepository.findByUserId(userId)
                .stream()
                .map(f -> new FavoritesController.FavoriteResponse(f.id.courseId.toString()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void add(UUID userId, UUID courseId) {
        if (!favoriteRepository.existsByIdUserIdAndIdCourseId(userId, courseId)) {
            CourseFavorite fav = new CourseFavorite();
            fav.id.userId = userId;
            fav.id.courseId = courseId;
            favoriteRepository.save(fav);
        }
    }

    @Transactional
    public void remove(UUID userId, UUID courseId) {
        favoriteRepository.deleteByIdUserIdAndIdCourseId(userId, courseId);
    }
}