package com.tribo.modules.certificate.controller;

import com.tribo.modules.certificate.entity.Certificate;
import com.tribo.modules.certificate.service.CertificateService;
import com.tribo.modules.user.entity.User;
import com.tribo.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
@Tag(name = "Certificados", description = "Certificados de conclusão de curso")
public class CertificateController {

    private final CertificateService certificateService;

    @Operation(summary = "Listar certificados do aluno autenticado")
    @GetMapping("/me")
    public ResponseEntity<List<CertificateResponse>> myCertificates(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                certificateService.listByUser(user.getId())
                        .stream().map(this::toResponse).toList()
        );
    }

    @Operation(summary = "Verificar autenticidade de um certificado pelo código público")
    @GetMapping("/verify/{code}")
    public ResponseEntity<CertificateResponse> verify(@PathVariable String code) {
        Certificate cert = certificateService.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Certificado não encontrado"));
        return ResponseEntity.ok(toResponse(cert));
    }

    private CertificateResponse toResponse(Certificate c) {
        return new CertificateResponse(
                c.getId().toString(),
                c.getUserName(),
                c.getCourseTitle(),
                c.getVerificationCode(),
                c.getIssuedAt()
        );
    }

    public record CertificateResponse(
            String id, String userName, String courseTitle,
            String verificationCode, OffsetDateTime issuedAt
    ) {}
}
