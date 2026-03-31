package com.tribo.modules.certificate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "certificates",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "course_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_name", nullable = false, length = 120)
    private String userName;

    @Column(name = "course_title", nullable = false, length = 200)
    private String courseTitle;

    /** Código único público para verificação: ex. TRIBO-2026-A3F9K2 */
    @Column(name = "verification_code", nullable = false, unique = true, length = 30)
    private String verificationCode;

    @Column(name = "issued_at", nullable = false)
    @Builder.Default
    private OffsetDateTime issuedAt = OffsetDateTime.now();
}
