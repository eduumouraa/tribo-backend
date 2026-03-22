package com.tribo.modules.course.controller;

import com.tribo.modules.course.entity.Course;
import com.tribo.modules.course.entity.Lesson;
import com.tribo.modules.course.entity.Module;
import com.tribo.modules.course.service.CourseService;
import com.tribo.modules.course.service.VideoStreamService;
import com.tribo.modules.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Cursos", description = "Listagem, detalhe e stream de aulas")
public class CourseController {

    private final CourseService courseService;
    private final VideoStreamService videoStreamService;

    @Operation(summary = "Listar cursos publicados com paginação")
    @GetMapping("/courses")
    public ResponseEntity<Page<CourseResponse>> findAll(
            @PageableDefault(size = 20, sort = "sortOrder") Pageable pageable
    ) {
        Page<Course> page = courseService.findPublished(pageable);
        return ResponseEntity.ok(page.map(this::toCourseResponse));
    }

    @Operation(summary = "Cursos em destaque para o hero banner")
    @GetMapping("/courses/featured")
    public ResponseEntity<List<CourseResponse>> featured() {
        List<Course> courses = courseService.findFeatured();
        return ResponseEntity.ok(courses.stream().map(this::toCourseResponse).collect(Collectors.toList()));
    }

    @Operation(summary = "Detalhe completo do curso com módulos e aulas")
    @GetMapping("/courses/{slug}")
    public ResponseEntity<CourseDetailResponse> findBySlug(@PathVariable String slug) {
        Course course = courseService.findBySlug(slug);
        return ResponseEntity.ok(toCourseDetailResponse(course));
    }

    @Operation(summary = "Gerar URL de stream para uma aula")
    @GetMapping("/lessons/{lessonId}/stream")
    public ResponseEntity<StreamResponse> getStreamUrl(
            @PathVariable UUID lessonId,
            @AuthenticationPrincipal User currentUser
    ) {
        String url = courseService.generateStreamUrl(lessonId, currentUser.getId());
        Instant expiresAt = videoStreamService.getExpiration();
        return ResponseEntity.ok(new StreamResponse(url, "panda", expiresAt.toString()));
    }

    // ── Mappers ──────────────────────────────────────────────────

    private CourseResponse toCourseResponse(Course c) {
        return new CourseResponse(
                c.getId().toString(), c.getTitle(), c.getSlug(), c.getDescription(),
                c.getThumbnailUrl(), c.getCategory(), c.getBadge(),
                c.getStatus().name(), c.getLessonsCount(), c.getDurationSecs()
        );
    }

    private LessonResponse toLessonResponse(Lesson l) {
        return new LessonResponse(
                l.getId().toString(), l.getTitle(), l.getDescription(),
                l.getDurationSecs(), l.getIsPreview(), l.getStatus().name(), l.getSortOrder()
        );
    }

    private ModuleResponse toModuleResponse(Module m) {
        return new ModuleResponse(
                m.getId().toString(), m.getTitle(), m.getSortOrder(),
                m.getLessons().stream().map(this::toLessonResponse).collect(Collectors.toList())
        );
    }

    private CourseDetailResponse toCourseDetailResponse(Course c) {
        return new CourseDetailResponse(
                c.getId().toString(), c.getTitle(), c.getSlug(), c.getDescription(),
                c.getThumbnailUrl(), c.getCategory(), c.getBadge(),
                c.getStatus().name(), c.getLessonsCount(), c.getDurationSecs(),
                c.getModules().stream().map(this::toModuleResponse).collect(Collectors.toList()),
                c.getMetadata()
        );
    }

    // ── DTOs ─────────────────────────────────────────────────────

    public record StreamResponse(String streamUrl, String provider, String expiresAt) {}

    public record CourseResponse(
            String id, String title, String slug, String description,
            String thumbnailUrl, String category, String badge,
            String status, int lessonsCount, int durationSecs
    ) {}

    public record LessonResponse(
            String id, String title, String description,
            int durationSecs, boolean isPreview, String status, int sortOrder
    ) {}

    public record ModuleResponse(
            String id, String title, int sortOrder, List<LessonResponse> lessons
    ) {}

    public record CourseDetailResponse(
            String id, String title, String slug, String description,
            String thumbnailUrl, String category, String badge,
            String status, int lessonsCount, int durationSecs,
            List<ModuleResponse> modules, Object metadata
    ) {}
}
