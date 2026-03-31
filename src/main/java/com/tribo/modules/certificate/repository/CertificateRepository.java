package com.tribo.modules.certificate.repository;

import com.tribo.modules.certificate.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    List<Certificate> findByUserIdOrderByIssuedAtDesc(UUID userId);

    Optional<Certificate> findByVerificationCode(String code);

    boolean existsByUserIdAndCourseId(UUID userId, UUID courseId);
}
