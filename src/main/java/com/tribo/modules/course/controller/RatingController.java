package com.tribo.modules.course.controller;

import com.tribo.modules.course.entity.CourseRating;
import com.tribo.modules.course.service.RatingService;
import com.tribo.modules.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/ratings")
@RequiredArgsConstructor
@Tag(name = "Avaliações", description = "Avaliações por estrelas de cursos")
public class RatingController {

    private final RatingService ratingService;

    @Operation(summary = "Listar avaliações de um curso")
    @GetMapping
    public ResponseEntity<RatingsResponse> list(
            @PathVariable UUID courseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<CourseRating> ratings = ratingService.listRatings(courseId, page, size);
        List<RatingDto> dtos = ratings.getContent().stream()
                .map(r -> new RatingDto(r.getId().toString(), r.getUserId().toString(),
                        r.getRating(), r.getReview(), r.getCreatedAt().toString()))
                .toList();
        return ResponseEntity.ok(new RatingsResponse(dtos, ratings.getTotalElements()));
    }

    @Operation(summary = "Minha avaliação neste curso")
    @GetMapping("/me")
    public ResponseEntity<RatingDto> myRating(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal User currentUser
    ) {
        CourseRating r = ratingService.getMyRating(courseId, currentUser.getId());
        if (r == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(new RatingDto(r.getId().toString(), r.getUserId().toString(),
                r.getRating(), r.getReview(), r.getCreatedAt().toString()));
    }

    @Operation(summary = "Avaliar curso (cria ou atualiza)")
    @PostMapping
    public ResponseEntity<RatingDto> upsert(
            @PathVariable UUID courseId,
            @Valid @RequestBody RatingRequest req,
            @AuthenticationPrincipal User currentUser
    ) {
        CourseRating r = ratingService.upsertRating(courseId, currentUser.getId(), req.rating(), req.review());
        return ResponseEntity.ok(new RatingDto(r.getId().toString(), r.getUserId().toString(),
                r.getRating(), r.getReview(), r.getUpdatedAt().toString()));
    }

    // ── DTOs ─────────────────────────────────────────────────────

    public record RatingRequest(
            @Min(1) @Max(5) int rating,
            String review
    ) {}

    public record RatingDto(
            String id, String userId, int rating, String review, String date
    ) {}

    public record RatingsResponse(List<RatingDto> ratings, long total) {}
}
