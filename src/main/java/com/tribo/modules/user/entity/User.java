package com.tribo.modules.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Entidade central de usuários.
 *
 * Implementa UserDetails para integração direta com Spring Security —
 * assim não precisamos de um wrapper separado.
 *
 * Roles disponíveis:
 * - STUDENT  → aluno comum com assinatura ativa
 * - ADMIN    → pode gerenciar cursos e usuários
 * - OWNER    → acesso total ao sistema
 * - SUPPORT  → pode ver dados de alunos mas não editar cursos
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /**
     * Role do usuário no sistema.
     * Armazenado como string no banco para facilitar leitura direta.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.STUDENT;

    /**
     * Status da conta — PENDING até confirmar o email,
     * ACTIVE após confirmação, SUSPENDED em caso de fraude.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ── Campos calculados (não persistidos) ──────────────────────

    /**
     * Retorna true se a conta está ativa E tem assinatura ativa.
     * O frontend usa esse campo para redirecionar para /oferta.
     * A verificação de assinatura é feita pelo SubscriptionService.
     */
    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    // ── UserDetails — integração com Spring Security ─────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Prefixo ROLE_ é exigido pelo Spring Security para @PreAuthorize("hasRole('ADMIN')")
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return this.passwordHash;
    }

    @Override
    public String getUsername() {
        return this.email;    // email é o identificador único
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return this.status != AccountStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.status == AccountStatus.ACTIVE;
    }

    // ── Enums ────────────────────────────────────────────────────

    public enum Role {
        STUDENT, ADMIN, OWNER, SUPPORT
    }

    public enum AccountStatus {
        PENDING,    // aguardando confirmação de email
        ACTIVE,     // conta ativa
        SUSPENDED   // suspensa por fraude ou inadimplência
    }
}
