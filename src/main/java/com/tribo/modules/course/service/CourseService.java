package com.tribo.modules.course.service;

import com.tribo.modules.course.entity.Course;
import com.tribo.modules.course.entity.Lesson;
import com.tribo.modules.course.entity.Module;
import com.tribo.modules.course.repository.CourseRepository;
import com.tribo.modules.course.repository.LessonRepository;
import com.tribo.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final VideoStreamService videoStreamService;

    @Value("${panda.api-key:}")
    private String pandaApiKey;

    @Transactional(readOnly = true)
    public Page<Course> findPublished(Pageable pageable) {
        log.debug("Buscando cursos publicados, pageable={}", pageable);
        return courseRepository.findPublished(pageable);
    }

    @Transactional(readOnly = true)
    public List<Course> findFeatured() {
        log.debug("Buscando cursos em destaque");
        return courseRepository.findFeatured();
    }

    /**
     * Retorna o detalhe completo de um curso pelo slug.
     *
     * O JOIN FETCH pode retornar módulos e aulas duplicadas no resultado
     * do Hibernate quando há múltiplas coleções. Fazemos a deduplicação
     * manualmente garantindo ordem correta por sortOrder.
     */
    @Transactional(readOnly = true)
    public Course findBySlug(String slug) {
        log.debug("Buscando curso por slug={}", slug);
        Course course = courseRepository.findBySlugWithModulesAndLessons(slug)
                .filter(c -> c.getStatus() == Course.CourseStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Curso não encontrado: " + slug));

        // Deduplica módulos por ID mantendo ordem por sortOrder
        List<Module> dedupedModules = course.getModules().stream()
                .collect(Collectors.toMap(
                        Module::getId,
                        m -> m,
                        (a, b) -> a,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparingInt(Module::getSortOrder))
                .collect(Collectors.toList());

        // Deduplica aulas de cada módulo por ID mantendo ordem por sortOrder
        dedupedModules.forEach(mod -> {
            List<Lesson> dedupedLessons = mod.getLessons().stream()
                    .collect(Collectors.toMap(
                            Lesson::getId,
                            l -> l,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ))
                    .values()
                    .stream()
                    .sorted(Comparator.comparingInt(Lesson::getSortOrder))
                    .collect(Collectors.toList());
            mod.setLessons(new LinkedHashSet<>(dedupedLessons));
        });

        course.setModules(dedupedModules);

        return course;
    }

    @Transactional(readOnly = true)
    public String generateStreamUrl(UUID lessonId, UUID userId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Aula não encontrada."));

        if (lesson.getVideoKey() == null || lesson.getVideoKey().isBlank()) {
            throw new ResourceNotFoundException("Vídeo ainda não disponível para esta aula.");
        }

        log.info("Gerando URL de stream para lessonId={}, userId={}, provider={}",
                lessonId, userId, lesson.getVideoProvider());

        return videoStreamService.generateUrl(lesson.getVideoKey(), lesson.getVideoProvider());
    }
}
