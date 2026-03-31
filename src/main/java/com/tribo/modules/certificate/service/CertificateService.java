package com.tribo.modules.certificate.service;

import com.tribo.modules.certificate.entity.Certificate;
import com.tribo.modules.certificate.repository.CertificateRepository;
import com.tribo.modules.course.repository.CourseRepository;
import com.tribo.modules.notification.service.NotificationService;
import com.tribo.modules.progress.repository.ProgressRepository;
import com.tribo.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final ProgressRepository progressRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Verifica se o aluno completou 100% do curso e emite o certificado.
     * Chamado de forma assíncrona após cada aula concluída.
     */
    @Async
    @Transactional
    public void generateIfCompleted(UUID userId, UUID courseId) {
        if (certificateRepository.existsByUserIdAndCourseId(userId, courseId)) return;

        int totalLessons = courseRepository.countPublishedLessons(courseId);
        if (totalLessons == 0) return;

        long completedLessons = progressRepository.countByUserIdAndCourseIdAndIsCompleted(userId, courseId, true);
        if (completedLessons < totalLessons) return;

        userRepository.findById(userId).ifPresent(user ->
            courseRepository.findById(courseId).ifPresent(course -> {
                Certificate cert = Certificate.builder()
                        .userId(userId)
                        .courseId(courseId)
                        .userName(user.getName())
                        .courseTitle(course.getTitle())
                        .verificationCode(generateCode(course.getSlug()))
                        .build();

                certificateRepository.save(cert);

                notificationService.create(userId, "certificate",
                        "Certificado emitido!",
                        "Parabéns! Seu certificado de conclusão do curso " + course.getTitle() + " está disponível.",
                        Map.of("courseId", courseId.toString(), "verificationCode", cert.getVerificationCode()));

                log.info("Certificado emitido para userId={}, courseId={}, code={}", userId, courseId, cert.getVerificationCode());
            })
        );
    }

    @Transactional(readOnly = true)
    public List<Certificate> listByUser(UUID userId) {
        return certificateRepository.findByUserIdOrderByIssuedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Certificate> findByCode(String code) {
        return certificateRepository.findByVerificationCode(code);
    }

    /** Gera código único legível: ex. TRIBO-2026-A3F9K2 */
    private String generateCode(String slug) {
        String prefix = slug != null && slug.contains("financas") ? "FIN" : "TRIBO";
        String year = String.valueOf(Year.now().getValue());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return prefix + "-" + year + "-" + random;
    }
}
