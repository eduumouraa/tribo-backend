package com.tribo.modules.user.controller;

import com.tribo.modules.auth.dto.AuthDTOs.UserResponse;
import com.tribo.modules.auth.dto.AuthDTOs.UserResponseMapper;
import com.tribo.modules.auth.service.SubscriptionService;
import com.tribo.modules.user.entity.User;
import com.tribo.modules.user.repository.UserRepository;
import com.tribo.shared.exception.ResourceNotFoundException;
import com.tribo.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Usuários", description = "Perfil e gestão de usuários")
public class UserController {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "Dados do usuário logado")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal User currentUser) {
        boolean hasSubscription = hasSubscriptionSafely(currentUser);
        return ResponseEntity.ok(UserResponseMapper.from(currentUser, hasSubscription));
    }

    @Operation(summary = "Atualizar perfil — nome e avatar")
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @AuthenticationPrincipal User currentUser,
            @RequestBody UpdateProfileRequest request
    ) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);

        log.info("Perfil atualizado para userId={}", user.getId());
        boolean hasSubscription = hasSubscriptionSafely(user);
        return ResponseEntity.ok(UserResponseMapper.from(user, hasSubscription));
    }

    @Operation(summary = "Trocar senha — exige a senha atual para confirmar")
    @PostMapping("/me/password")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal User currentUser,
            @RequestBody ChangePasswordRequest request
    ) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Senha atual incorreta.");
        }

        if (request.newPassword() == null || request.newPassword().length() < 8) {
            throw new BusinessException("A nova senha deve ter pelo menos 8 caracteres.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);

        log.info("Senha alterada para userId={}", user.getId());
        return ResponseEntity.ok(new MessageResponse("Senha alterada com sucesso."));
    }

    @Operation(summary = "Admin: ver dados de qualquer usuário")
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'SUPPORT')")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        boolean hasSubscription = hasSubscriptionSafely(user);
        return ResponseEntity.ok(UserResponseMapper.from(user, hasSubscription));
    }

    private boolean hasSubscriptionSafely(User user) {
        if (user.getRole() == User.Role.ADMIN || user.getRole() == User.Role.OWNER) return true;
        try {
            return subscriptionService.hasActiveSubscription(user.getId());
        } catch (Exception e) {
            log.warn("Erro ao verificar assinatura para userId={}: {}", user.getId(), e.getMessage());
            return false;
        }
    }

    public record UpdateProfileRequest(
            @Size(min = 2, max = 120) String name,
            String avatarUrl
    ) {}

    public record ChangePasswordRequest(
            String currentPassword,
            @Size(min = 8, max = 72) String newPassword
    ) {}

    public record MessageResponse(String message) {}
}
