package com.tribo.modules.course.service;

import com.tribo.modules.course.entity.Course;
import com.tribo.modules.course.entity.CourseRating;
import com.tribo.modules.course.repository.CourseRatingRepository;
import com.tribo.modules.course.repository.CourseRepository;
import com.tribo.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingService {

    private final CourseRatingRepository ratingRepository;
    private final CourseRepository courseRepository;

    /**
     * Cria ou atualiza avaliação do usuário para um curso.
     * Recalcula rating_avg e rating_count no curso (desnormalizado).
     */
    @Transactional
    public CourseRating upsertRating(UUID courseId, UUID userId, int stars, String review) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Avaliação deve ser entre 1 e 5 estrelas.");
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Curso não encontrado."));

        CourseRating rating = ratingRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseGet(() -> CourseRating.builder()
                        .courseId(courseId)
                        .userId(userId)
                        .build());

        rating.setRating(stars);
        rating.setReview(review);
        rating.setUpdatedAt(OffsetDateTime.now());
        ratingRepository.save(rating);

        // Atualiza cache desnormalizado no curso
        Double avg = ratingRepository.findAverageByCourseId(courseId);
        int count = ratingRepository.countByCourseId(courseId);
        course.setRatingAvg(avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
        course.setRatingCount(count);
        courseRepository.save(course);

        log.info("Avaliação salva: courseId={}, userId={}, stars={}", courseId, userId, stars);
        return rating;
    }

    @Transactional(readOnly = true)
    public Page<CourseRating> listRatings(UUID courseId, int page, int size) {
        return ratingRepository.findByCourseIdOrderByCreatedAtDesc(courseId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public CourseRating getMyRating(UUID courseId, UUID userId) {
        return ratingRepository.findByCourseIdAndUserId(courseId, userId).orElse(null);
    }
}
