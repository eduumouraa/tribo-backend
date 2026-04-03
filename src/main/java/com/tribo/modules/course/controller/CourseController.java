package com.tribo.modules.course.controller;

import com.tribo.modules.auth.service.SubscriptionService;
import com.tribo.modules.course.entity.Course;
import com.tribo.modules.course.entity.Lesson;
import com.tribo.modules.course.entity.Module;
import com.tribo.modules.course.repository.CourseRepository;
import com.tribo.modules.course.repository.LessonMaterialRepository;
import com.tribo.modules.course.service.CourseService;
import com.tribo.modules.course.service.VideoStreamService;
import com.tribo.modules.user.entity.User;
import com.tribo.shared.exception.BusinessException;
import com.tribo.shared.exception.PlanAccessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cursos", description = "Listagem, detalhe e stream de aulas")
public class CourseController {

    private final CourseService courseService;
    private final VideoStreamService videoStreamService;
    private final SubscriptionService subscriptionService;
    private final CourseRepository courseRepository;
    private final LessonMaterialRepository materialRepository;

    // ── Endpoints públicos ────────────────────────────────────────

    @Operation(summary = "Listar cursos publicados com paginação")
    @GetMapping("/courses")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<CourseResponse>> findAll(
            @PageableDefault(size = 20, sort = "sortOrder") Pageable pageable
    ) {
        Page<Course> page = courseService.findPublished(pageable);
        return ResponseEntity.ok(page.map(this::toCourseResponse));
    }

    @Operation(summary = "Cursos em destaque para o hero banner")
    @GetMapping("/courses/featured")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CourseResponse>> featured() {
        List<Course> courses = courseService.findFeatured();
        return ResponseEntity.ok(courses.stream().map(this::toCourseResponse).collect(Collectors.toList()));
    }

    @Operation(summary = "Detalhe completo do curso com módulos e aulas")
    @GetMapping("/courses/{slug}")
    public ResponseEntity<CourseDetailResponse> findBySlug(
            @PathVariable String slug,
            @AuthenticationPrincipal User currentUser
    ) {
        Course course = courseService.findBySlug(slug);

        boolean isAdmin = isAdminOrOwner(currentUser);
        boolean hasAccess = isAdmin || hasPlanAccess(currentUser, course.getRequiredPlan());

        // Se não tem acesso, inclui qual plano é necessário para o frontend exibir upsell
        String requiredPlanForFrontend = hasAccess ? null : course.getRequiredPlan();

        return ResponseEntity.ok(toCourseDetailResponse(course, hasAccess, requiredPlanForFrontend));
    }

    // ── Endpoints protegidos ─────────────────────────────────────

    @Operation(summary = "Gerar URL de stream para uma aula — requer plano ativo")
    @GetMapping("/lessons/{lessonId}/stream")
    public ResponseEntity<StreamResponse> getStreamUrl(
            @PathVariable UUID lessonId,
            @AuthenticationPrincipal User currentUser
    ) {
        if (!isAdminOrOwner(currentUser)) {
            // Busca a aula para saber qual curso/plano é necessário
            String requiredPlan = courseRepository.findRequiredPlanByLessonId(lessonId);

            if (requiredPlan != null && !"free".equals(requiredPlan)) {
                if (!subscriptionService.hasAccessToCourse(currentUser.getId(), requiredPlan)) {
                    // Busca o slug para o erro
                    String courseSlug = courseRepository.findSlugByLessonId(lessonId);
                    throw new PlanAccessException(requiredPlan, courseSlug);
                }
            }
        }

        String url = courseService.generateStreamUrl(lessonId, currentUser.getId());
        Instant expiresAt = videoStreamService.getExpiration();

        log.info("Stream URL gerada para lessonId={}, userId={}", lessonId, currentUser.getId());
        return ResponseEntity.ok(new StreamResponse(url, "panda", expiresAt.toString()));
    }

    // ── Helpers ──────────────────────────────────────────────────

    private boolean isAdminOrOwner(User user) {
        if (user == null) return false;
        return user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.OWNER;
    }

    private boolean hasPlanAccess(User user, String requiredPlan) {
        if (user == null) return false;
        if (requiredPlan == null || "free".equals(requiredPlan)) return true;
        try {
            return subscriptionService.hasAccessToCourse(user.getId(), requiredPlan);
        } catch (Exception e) {
            log.warn("Erro ao verificar acesso por plano para userId={}: {}", user.getId(), e.getMessage());
            return false;
        }
    }

    // ── Mappers ──────────────────────────────────────────────────

    private CourseResponse toCourseResponse(Course c) {
        int lessonsCount = courseRepository.countPublishedLessons(c.getId());
        int durationSecs = courseRepository.sumPublishedDuration(c.getId());
        return new CourseResponse(
                c.getId().toString(), c.getTitle(), c.getSlug(), c.getDescription(),
                c.getThumbnailUrl(), c.getCategory(), c.getBadge(),
                c.getStatus().name(), lessonsCount, durationSecs,
                c.getRequiredPlan(), c.getRatingAvg(), c.getRatingCount()
        );
    }

    private LessonResponse toLessonResponse(Lesson l, boolean hasAccess, int materialsCount) {
        String videoKey = (hasAccess || l.getIsPreview()) ? l.getVideoKey() : null;
        boolean locked = !l.isAvailableNow();
        String availableAt = l.getAvailableAt() != null ? l.getAvailableAt().toString() : null;
        return new LessonResponse(
                l.getId().toString(), l.getTitle(), l.getDescription(),
                l.getDurationSecs(), l.getIsPreview(), l.getStatus().name(),
                l.getSortOrder(), videoKey, l.getVideoProvider(),
                locked, availableAt, materialsCount
        );
    }

    private ModuleResponse toModuleResponse(Module m, boolean hasAccess, Map<UUID, Integer> materialCounts) {
        return new ModuleResponse(
                m.getId().toString(), m.getTitle(), m.getSortOrder(),
                m.getLessons().stream()
                        .map(l -> toLessonResponse(l, hasAccess, materialCounts.getOrDefault(l.getId(), 0)))
                        .collect(Collectors.toList())
        );
    }

    private CourseDetailResponse toCourseDetailResponse(Course c, boolean hasAccess, String requiredPlan) {
        int lessonsCount = courseRepository.countPublishedLessons(c.getId());
        int durationSecs = courseRepository.sumPublishedDuration(c.getId());

        // Batch: contagem de materiais por aula — evita N+1
        List<UUID> allLessonIds = c.getModules().stream()
                .flatMap(m -> m.getLessons().stream())
                .map(Lesson::getId)
                .collect(Collectors.toList());
        Map<UUID, Integer> materialCounts = allLessonIds.isEmpty()
                ? Map.of()
                : materialRepository.countByLessonIds(allLessonIds).stream()
                        .collect(Collectors.toMap(
                                row -> (UUID) row[0],
                                row -> ((Number) row[1]).intValue()
                        ));

        return new CourseDetailResponse(
                c.getId().toString(), c.getTitle(), c.getSlug(), c.getDescription(),
                c.getThumbnailUrl(), c.getCategory(), c.getBadge(),
                c.getStatus().name(), lessonsCount, durationSecs, hasAccess,
                c.getModules().stream()
                        .map(m -> toModuleResponse(m, hasAccess, materialCounts))
                        .collect(Collectors.toList()),
                c.getMetadata(),
                requiredPlan,
                requiredPlan != null ? "/checkout?plan=" + requiredPlan : null,
                c.getRatingAvg(), c.getRatingCount()
        );
    }

    // ── DTOs ─────────────────────────────────────────────────────

    public record StreamResponse(String streamUrl, String provider, String expiresAt) {}

    public record CourseResponse(
            String id, String title, String slug, String description,
            String thumbnailUrl, String category, String badge,
            String status, int lessonsCount, int durationSecs,
            String requiredPlan, double ratingAvg, int ratingCount
    ) {}

    public record LessonResponse(
            String id, String title, String description,
            int durationSecs, boolean isPreview, String status,
            int sortOrder, String videoKey, String videoProvider,
            boolean locked, String availableAt, int materialsCount
    ) {}

    public record ModuleResponse(
            String id, String title, int sortOrder, List<LessonResponse> lessons
    ) {}

    public record CourseDetailResponse(
            String id, String title, String slug, String description,
            String thumbnailUrl, String category, String badge,
            String status, int lessonsCount, int durationSecs,
            boolean hasAccess,
            List<ModuleResponse> modules, Object metadata,
            String requiredPlan,
            String upgradeUrl,
            double ratingAvg, int ratingCount
    ) {}
}
