package com.tribo.modules.course.service;

import com.tribo.modules.course.entity.Course;
import com.tribo.modules.course.entity.Lesson;
import com.tribo.modules.course.repository.CourseRepository;
import com.tribo.modules.course.repository.LessonRepository;
import com.tribo.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Serviço de cursos.
 *
 * Cache Redis é usado nos endpoints mais acessados (listagem e detalhe).
 * O cache é invalidado quando um admin publica alterações.
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
     * Cache de 15 minutos — cursos não mudam com frequência.
     */
    @Cacheable(value = "courses:published", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<Course> findPublished(Pageable pageable) {
        return courseRepository.findByStatus(Course.CourseStatus.PUBLISHED, pageable);
    }

    /**
     * Retorna os cursos em destaque para o hero banner.
     */
    @Cacheable(value = "courses:featured")
    public List<Course> findFeatured() {
        return courseRepository.findFeatured();
    }

    /**
     * Retorna o detalhe completo de um curso pelo slug, incluindo módulos e aulas.
     * Apenas cursos publicados são visíveis para alunos.
     */
    @Cacheable(value = "course:detail", key = "#slug")
    public Course findBySlug(String slug) {
        return courseRepository.findBySlug(slug)
                .filter(c -> c.getStatus() == Course.CourseStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Curso não encontrado: " + slug));
    }

    /**
     * Gera a URL de stream para uma aula específica.
     *
     * Fluxo:
     * 1. Busca a aula no banco e verifica se o usuário tem acesso
     * 2. Chama o Panda Video (ou S3) para gerar uma URL assinada temporária
     * 3. A URL expira em 2 horas — o player a renova automaticamente
     *
     * @param lessonId ID da aula
     * @param userId   ID do usuário (para verificação de acesso)
     */
    public String generateStreamUrl(UUID lessonId, UUID userId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Aula não encontrada."));

        if (lesson.getVideoKey() == null || lesson.getVideoKey().isBlank()) {
            throw new ResourceNotFoundException("Vídeo ainda não disponível para esta aula.");
        }

        return videoStreamService.generateUrl(lesson.getVideoKey(), lesson.getVideoProvider());
    }
}
