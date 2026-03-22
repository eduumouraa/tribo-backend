package com.tribo.modules.user.controller;

import com.tribo.modules.auth.dto.AuthDTOs.UserResponse;
import com.tribo.modules.auth.dto.AuthDTOs.UserResponseMapper;
import com.tribo.modules.auth.service.SubscriptionService;
import com.tribo.modules.user.entity.User;
import com.tribo.modules.user.repository.UserRepository;
import com.tribo.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Perfil e gestão de usuários")
public class UserController {

    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "Dados do usuário logado")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal User currentUser) {
        boolean hasSubscription = subscriptionService.hasActiveSubscription(currentUser.getId());
        return ResponseEntity.ok(UserResponseMapper.from(currentUser, hasSubscription));
    }

    @Operation(summary = "Atualizar perfil")
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @AuthenticationPrincipal User currentUser,
            @RequestBody UpdateProfileRequest request
    ) {
        if (request.name() != null && !request.name().isBlank()) {
            currentUser.setName(request.name());
        }
        if (request.avatarUrl() != null) {
            currentUser.setAvatarUrl(request.avatarUrl());
        }
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            currentUser.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }
        userRepository.save(currentUser);
        boolean hasSubscription = subscriptionService.hasActiveSubscription(currentUser.getId());
        return ResponseEntity.ok(UserResponseMapper.from(currentUser, hasSubscription));
    }

    @Operation(summary = "Admin: ver dados de qualquer usuário")
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'SUPPORT')")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        boolean hasSubscription = subscriptionService.hasActiveSubscription(userId);
        return ResponseEntity.ok(UserResponseMapper.from(user, hasSubscription));
    }

    public record UpdateProfileRequest(
            @Size(min = 2, max = 120) String name,
            String avatarUrl,
            @Size(min = 8, max = 72) String newPassword
    ) {}
}
