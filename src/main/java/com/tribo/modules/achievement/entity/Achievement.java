package com.tribo.modules.achievement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "achievements",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "type"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Tipos disponíveis:
     * PRIMEIRA_AULA     — completou a primeira aula
     * MARATONISTA       — assistiu 5 horas no total
     * DEDICADO          — assistiu 20 horas no total
     * CURSO_COMPLETO    — completou qualquer curso
     * TRIBO_COMPLETA    — completou o curso Tribo do Investidor
     * FINANCAS_COMPLETA — completou o curso Organização Financeira
     */
    @Column(nullable = false, length = 100)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "earned_at", nullable = false)
    @Builder.Default
    private OffsetDateTime earnedAt = OffsetDateTime.now();
}
