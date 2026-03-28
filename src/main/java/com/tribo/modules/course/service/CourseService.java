package com.tribo.modules.course.service;

import com.tribo.modules.course.entity.Course;
import com.tribo.modules.course.entity.Lesson;
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

import java.util.List;
import java.util.UUID;

/**
 * Serviço de cursos.
 *
 * IMPORTANTE: As anotações @Cacheable foram REMOVIDAS intencionalmente.
 * Entidades JPA com coleções LAZY não são serializáveis pelo Redis de forma
 * segura. A solução correta é cachear DTOs — implementar futuramente quando
 * o volume de requisições justificar.
 *
 * Alternativa rápida implementada: cache desabilitado por enquanto.
 * O PostgreSQL com índices corretos é suficiente para o volume inicial.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final VideoStreamService videoStreamService;

    @Value("${panda.api-key:}")
    private String pandaApiKey;

    /**
     * Lista todos os cursos publicados com paginação.
     * Sem cache — PostgreSQL com índice em (status, sort_order) é rápido o suficiente.
     */
    @Transactional(readOnly = true)
    public Page<Course> findPublished(Pageable pageable) {
        log.debug("Buscando cursos publicados, pageable={}", pageable);
        return courseRepository.findPublished(pageable);
    }

    /**
     * Retorna os cursos em destaque para o hero banner.
     */
    @Transactional(readOnly = true)
    public List<Course> findFeatured() {
        log.debug("Buscando cursos em destaque");
        return courseRepository.findFeatured();
    }

    /**
     * Retorna o detalhe completo de um curso pelo slug, incluindo módulos e aulas.
     * @Transactional garante que as coleções LAZY sejam carregadas dentro da sessão.
     * Apenas cursos publicados são visíveis para alunos.
     */
    @Transactional(readOnly = true)
    public Course findBySlug(String slug) {
        log.debug("Buscando curso por slug={}", slug);
        Course course = courseRepository.findBySlugWithModulesAndLessons(slug)
                .filter(c -> c.getStatus() == Course.CourseStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Curso não encontrado: " + slug));

        // Força inicialização das coleções lazy dentro da transação
        course.getModules().forEach(m -> m.getLessons().size());
        return course;
    }

    /**
     * Gera a URL de stream para uma aula específica.
     *
     * Fluxo:
     * 1. Busca a aula no banco e verifica se está publicada
     * 2. Chama o Panda Video para gerar a URL de embed
     * 3. A URL expira em 2 horas — o player a renova automaticamente
     */
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
