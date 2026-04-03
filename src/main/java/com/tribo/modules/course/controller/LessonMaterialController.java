package com.tribo.modules.course.controller;

import com.tribo.modules.course.entity.LessonMaterial;
import com.tribo.modules.course.repository.LessonMaterialRepository;
import com.tribo.modules.course.repository.LessonRepository;
import com.tribo.modules.user.entity.User;
import com.tribo.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lessons/{lessonId}/materials")
@RequiredArgsConstructor
@Tag(name = "Materiais", description = "Materiais de apoio por aula")
public class LessonMaterialController {

    private final LessonMaterialRepository materialRepository;
    private final LessonRepository lessonRepository;

    @Operation(summary = "Listar materiais de uma aula")
    @GetMapping
    public ResponseEntity<List<MaterialDto>> list(@PathVariable UUID lessonId) {
        List<MaterialDto> dtos = materialRepository
                .findByLessonIdOrderBySortOrderAsc(lessonId)
                .stream()
                .map(m -> new MaterialDto(m.getId().toString(), m.getTitle(), m.getUrl(), m.getType(), m.getSortOrder()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(summary = "Adicionar material (ADMIN/OWNER)")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<MaterialDto> create(
            @PathVariable UUID lessonId,
            @Valid @RequestBody MaterialRequest req,
            @AuthenticationPrincipal User currentUser
    ) {
        var lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Aula não encontrada."));

        LessonMaterial material = LessonMaterial.builder()
                .lesson(lesson)
                .title(req.title())
                .url(req.url())
                .type(req.type() != null ? req.type() : "link")
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .build();

        materialRepository.save(material);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MaterialDto(material.getId().toString(), material.getTitle(),
                        material.getUrl(), material.getType(), material.getSortOrder()));
    }

    @Operation(summary = "Remover material (ADMIN/OWNER)")
    @DeleteMapping("/{materialId}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID lessonId,
            @PathVariable UUID materialId
    ) {
        materialRepository.deleteById(materialId);
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ─────────────────────────────────────────────────────

    public record MaterialRequest(
            @NotBlank String title,
            @NotBlank String url,
            String type,
            Integer sortOrder
    ) {}

    public record MaterialDto(
            String id, String title, String url, String type, int sortOrder
    ) {}
}
