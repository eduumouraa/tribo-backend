package com.tribo.modules.course.controller;

import com.tribo.modules.course.entity.Course;
import com.tribo.modules.course.entity.Lesson;
import com.tribo.modules.course.entity.Module;
import com.tribo.modules.course.repository.CourseRepository;
import com.tribo.modules.course.repository.LessonRepository;
import com.tribo.modules.course.repository.ModuleRepository;
import com.tribo.shared.exception.BusinessException;
import com.tribo.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD administrativo de cursos, módulos e aulas.
 * Apenas ADMIN e OWNER têm acesso.
 *
 * Cursos:
 *   GET    /api/v1/admin/courses               — listar todos (incl. DRAFT)
 *   GET    /api/v1/admin/courses/{id}           — detalhe com módulos e aulas
 *   POST   /api/v1/admin/courses               — criar curso
 *   PUT    /api/v1/admin/courses/{id}          — editar curso
 *   PATCH  /api/v1/admin/courses/{id}/status   — publicar / arquivar
 *   DELETE /api/v1/admin/courses/{id}          — deletar curso
 *
 * Módulos:
 *   POST   /api/v1/admin/courses/{id}/modules  — adicionar módulo
 *   PUT    /api/v1/admin/modules/{id}          — editar módulo
 *   DELETE /api/v1/admin/modules/{id}          — deletar módulo
 *
 * Aulas:
 *   POST   /api/v1/admin/modules/{id}/lessons  — adicionar aula
 *   PUT    /api/v1/admin/lessons/{id}          — editar aula
 *   PATCH  /api/v1/admin/lessons/{id}/status   — publicar / rascunho
 *   DELETE /api/v1/admin/lessons/{id}          — deletar aula
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
@Tag(name = "Admin — Cursos", description = "CRUD administrativo de cursos, módulos e aulas")
public class AdminCourseController {

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;

    // ══════════════════════════════════════════════════════
    // CURSOS
    // ══════════════════════════════════════════════════════

    @Operation(summary = "Listar todos os cursos (DRAFT, PUBLISHED, ARCHIVED)")
    @GetMapping("/courses")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<CourseListItem>> listCourses(
            @PageableDefault(size = 20, sort = "sortOrder") Pageable pageable
    ) {
        Page<Course> page = courseRepository.findAll(pageable);
        return ResponseEntity.ok(page.map(c -> new CourseListItem(
                c.getId().toString(), c.getTitle(), c.getSlug(),
                c.getCategory(), c.getStatus().name(),
                c.getIsFeatured() != null && c.getIsFeatured(),
                c.getSortOrder() != null ? c.getSortOrder() : 0,
                courseRepository.countPublishedLessons(c.getId()),
                c.getCreatedAt() != null ? c.getCreatedAt().toString() : null
        )));
    }

    @Operation(summary = "Detalhe completo do curso com módulos e aulas (inclusive DRAFT)")
    @GetMapping("/courses/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<CourseAdminDetail> getCourse(@PathVariable UUID id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curso não encontrado"));
        return ResponseEntity.ok(toCourseAdminDetail(course));
    }

    @Operation(summary = "Criar novo curso")
    @PostMapping("/courses")
    @Transactional
    public ResponseEntity<CourseAdminDetail> createCourse(@Valid @RequestBody CourseRequest request) {
        if (courseRepository.findBySlug(request.slug()).isPresent()) {
            throw new BusinessException("Já existe um curso com o slug: " + request.slug());
        }

        Course course = Course.builder()
                .title(request.title())
                .slug(request.slug())
                .description(request.description())
                .thumbnailUrl(request.thumbnailUrl())
                .category(request.category())
                .badge(request.badge())
                .isFeatured(Boolean.TRUE.equals(request.isFeatured()))
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .status(Course.CourseStatus.DRAFT)
                .build();

        courseRepository.save(course);
        log.info("Curso criado: {} (slug={})", course.getTitle(), course.getSlug());
        return ResponseEntity.ok(toCourseAdminDetail(course));
    }

    @Operation(summary = "Editar curso")
    @PutMapping("/courses/{id}")
    @Transactional
    public ResponseEntity<CourseAdminDetail> updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody CourseRequest request
    ) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curso não encontrado"));

        courseRepository.findBySlug(request.slug())
                .filter(c -> !c.getId().equals(id))
                .ifPresent(c -> { throw new BusinessException(
                    "Slug já utilizado por outro curso: " + request.slug()); });

        course.setTitle(request.title());
        course.setSlug(request.slug());
        course.setDescription(request.description());
        course.setThumbnailUrl(request.thumbnailUrl());
        course.setCategory(request.category());
        course.setBadge(request.badge());
        course.setIsFeatured(Boolean.TRUE.equals(request.isFeatured()));
        if (request.sortOrder() != null) course.setSortOrder(request.sortOrder());
        course.setUpdatedAt(OffsetDateTime.now());

        courseRepository.save(course);
        log.info("Curso atualizado: {}", course.getSlug());
        return ResponseEntity.ok(toCourseAdminDetail(course));
    }

    @Operation(summary = "Alterar status do curso (DRAFT / PUBLISHED / ARCHIVED)")
    @PatchMapping("/courses/{id}/status")
    @Transactional
    public ResponseEntity<Map<String, String>> changeCourseStatus(
            @PathVariable UUID id,
            @RequestBody StatusRequest request
    ) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curso não encontrado"));

        Course.CourseStatus newStatus;
        try {
            newStatus = Course.CourseStatus.valueOf(request.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Status inválido. Use: DRAFT, PUBLISHED ou ARCHIVED");
        }

        course.setStatus(newStatus);
        course.setUpdatedAt(OffsetDateTime.now());
        courseRepository.save(course);

        log.info("Status do curso {} alterado para {}", course.getSlug(), newStatus);
        return ResponseEntity.ok(Map.of(
                "message", "Status alterado para " + newStatus,
                "slug", course.getSlug()
        ));
    }

    @Operation(summary = "Deletar curso (e todos os módulos e aulas)")
    @DeleteMapping("/courses/{id}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteCourse(@PathVariable UUID id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Curso não encontrado"));

        courseRepository.delete(course);
        log.info("Curso deletado: {}", course.getSlug());
        return ResponseEntity.ok(Map.of("message", "Curso removido com sucesso."));
    }

    // ══════════════════════════════════════════════════════
    // MÓDULOS
    // ══════════════════════════════════════════════════════

    @Operation(summary = "Adicionar módulo a um curso")
    @PostMapping("/courses/{courseId}/modules")
    @Transactional
    public ResponseEntity<ModuleAdminResponse> createModule(
            @PathVariable UUID courseId,
            @Valid @RequestBody ModuleRequest request
    ) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Curso não encontrado"));

        Module module = Module.builder()
                .course(course)
                .title(request.title())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .build();

        moduleRepository.save(module);
        log.info("Módulo '{}' criado no curso '{}'", module.getTitle(), course.getSlug());
        return ResponseEntity.ok(toModuleAdminResponse(module));
    }

    @Operation(summary = "Editar módulo")
    @PutMapping("/modules/{id}")
    @Transactional
    public ResponseEntity<ModuleAdminResponse> updateModule(
            @PathVariable UUID id,
            @Valid @RequestBody ModuleRequest request
    ) {
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo não encontrado"));

        module.setTitle(request.title());
        if (request.sortOrder() != null) module.setSortOrder(request.sortOrder());

        moduleRepository.save(module);
        return ResponseEntity.ok(toModuleAdminResponse(module));
    }

    @Operation(summary = "Deletar módulo e todas as suas aulas")
    @DeleteMapping("/modules/{id}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteModule(@PathVariable UUID id) {
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo não encontrado"));

        moduleRepository.delete(module);
        log.info("Módulo deletado: {}", module.getTitle());
        return ResponseEntity.ok(Map.of("message", "Módulo removido com sucesso."));
    }

    // ══════════════════════════════════════════════════════
    // AULAS
    // ══════════════════════════════════════════════════════

    @Operation(summary = "Adicionar aula a um módulo")
    @PostMapping("/modules/{moduleId}/lessons")
    @Transactional
    public ResponseEntity<LessonAdminResponse> createLesson(
            @PathVariable UUID moduleId,
            @Valid @RequestBody LessonRequest request
    ) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Módulo não encontrado"));

        Lesson lesson = Lesson.builder()
                .module(module)
                .title(request.title())
                .description(request.description())
                .videoKey(request.videoKey())
                .videoProvider(request.videoProvider() != null ? request.videoProvider() : "panda")
                .durationSecs(request.durationSecs() != null ? request.durationSecs() : 0)
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .isPreview(Boolean.TRUE.equals(request.isPreview()))
                .status(Lesson.LessonStatus.DRAFT)
                .build();

        lessonRepository.save(lesson);
        log.info("Aula '{}' criada no módulo '{}'", lesson.getTitle(), module.getTitle());
        return ResponseEntity.ok(toLessonAdminResponse(lesson));
    }

    @Operation(summary = "Editar aula")
    @PutMapping("/lessons/{id}")
    @Transactional
    public ResponseEntity<LessonAdminResponse> updateLesson(
            @PathVariable UUID id,
            @Valid @RequestBody LessonRequest request
    ) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aula não encontrada"));

        lesson.setTitle(request.title());
        if (request.description() != null) lesson.setDescription(request.description());
        if (request.videoKey() != null) lesson.setVideoKey(request.videoKey());
        if (request.videoProvider() != null) lesson.setVideoProvider(request.videoProvider());
        if (request.durationSecs() != null) lesson.setDurationSecs(request.durationSecs());
        if (request.sortOrder() != null) lesson.setSortOrder(request.sortOrder());
        if (request.isPreview() != null) lesson.setIsPreview(request.isPreview());
        if (request.status() != null) {
            try {
                lesson.setStatus(Lesson.LessonStatus.valueOf(request.status().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Status inválido. Use: DRAFT ou PUBLISHED");
            }
        }

        lessonRepository.save(lesson);
        return ResponseEntity.ok(toLessonAdminResponse(lesson));
    }

    @Operation(summary = "Alterar status da aula (DRAFT / PUBLISHED)")
    @PatchMapping("/lessons/{id}/status")
    @Transactional
    public ResponseEntity<Map<String, String>> changeLessonStatus(
            @PathVariable UUID id,
            @RequestBody StatusRequest request
    ) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aula não encontrada"));

        Lesson.LessonStatus newStatus;
        try {
            newStatus = Lesson.LessonStatus.valueOf(request.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Status inválido. Use: DRAFT ou PUBLISHED");
        }

        lesson.setStatus(newStatus);
        lessonRepository.save(lesson);

        log.info("Status da aula '{}' alterado para {}", lesson.getTitle(), newStatus);
        return ResponseEntity.ok(Map.of("message", "Status alterado para " + newStatus));
    }

    @Operation(summary = "Deletar aula")
    @DeleteMapping("/lessons/{id}")
    @Transactional
    public ResponseEntity<Map<String, String>> deleteLesson(@PathVariable UUID id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aula não encontrada"));

        lessonRepository.delete(lesson);
        log.info("Aula deletada: {}", lesson.getTitle());
        return ResponseEntity.ok(Map.of("message", "Aula removida com sucesso."));
    }

    // ══════════════════════════════════════════════════════
    // MAPPERS
    // ══════════════════════════════════════════════════════

    private CourseAdminDetail toCourseAdminDetail(Course c) {
        List<ModuleAdminResponse> modules = c.getModules().stream()
                .map(this::toModuleAdminResponse)
                .collect(Collectors.toList());
        return new CourseAdminDetail(
                c.getId().toString(), c.getTitle(), c.getSlug(), c.getDescription(),
                c.getThumbnailUrl(), c.getCategory(), c.getBadge(),
                c.getStatus().name(),
                c.getIsFeatured() != null && c.getIsFeatured(),
                c.getSortOrder() != null ? c.getSortOrder() : 0,
                modules,
                c.getCreatedAt() != null ? c.getCreatedAt().toString() : null,
                c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null
        );
    }

    private ModuleAdminResponse toModuleAdminResponse(Module m) {
        List<LessonAdminResponse> lessons = m.getLessons().stream()
                .map(this::toLessonAdminResponse)
                .collect(Collectors.toList());
        return new ModuleAdminResponse(
                m.getId().toString(), m.getTitle(),
                m.getSortOrder() != null ? m.getSortOrder() : 0,
                lessons
        );
    }

    private LessonAdminResponse toLessonAdminResponse(Lesson l) {
        return new LessonAdminResponse(
                l.getId().toString(), l.getTitle(), l.getDescription(),
                l.getVideoKey(), l.getVideoProvider(),
                l.getDurationSecs() != null ? l.getDurationSecs() : 0,
                l.getSortOrder() != null ? l.getSortOrder() : 0,
                l.getIsPreview() != null && l.getIsPreview(),
                l.getStatus().name()
        );
    }

    // ══════════════════════════════════════════════════════
    // DTOs — Requests
    // ══════════════════════════════════════════════════════

    public record CourseRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 200) String slug,
            String description,
            String thumbnailUrl,
            String category,
            String badge,
            Boolean isFeatured,
            Integer sortOrder
    ) {}

    public record ModuleRequest(
            @NotBlank @Size(max = 200) String title,
            Integer sortOrder
    ) {}

    public record LessonRequest(
            @NotBlank @Size(max = 300) String title,
            String description,
            String videoKey,
            String videoProvider,
            Integer durationSecs,
            Integer sortOrder,
            Boolean isPreview,
            String status
    ) {}

    public record StatusRequest(String status) {}

    // ══════════════════════════════════════════════════════
    // DTOs — Responses
    // ══════════════════════════════════════════════════════

    public record CourseListItem(
            String id, String title, String slug, String category,
            String status, boolean isFeatured, int sortOrder,
            int lessonsCount, String createdAt
    ) {}

    public record CourseAdminDetail(
            String id, String title, String slug, String description,
            String thumbnailUrl, String category, String badge,
            String status, boolean isFeatured, int sortOrder,
            List<ModuleAdminResponse> modules,
            String createdAt, String updatedAt
    ) {}

    public record ModuleAdminResponse(
            String id, String title, int sortOrder,
            List<LessonAdminResponse> lessons
    ) {}

    public record LessonAdminResponse(
            String id, String title, String description,
            String videoKey, String videoProvider,
            int durationSecs, int sortOrder,
            boolean isPreview, String status
    ) {}
}
