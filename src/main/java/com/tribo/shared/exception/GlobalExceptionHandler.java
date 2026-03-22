package com.tribo.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler global de exceções — garante que todos os erros
 * retornem JSON padronizado em vez de HTML do Tomcat.
 *
 * Formato de resposta (RFC 7807 Problem Details):
 * {
 *   "type": "about:blank",
 *   "title": "Bad Request",
 *   "status": 400,
 *   "detail": "Email já cadastrado."
 * }
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Erros de validação do @Valid — retorna mapa de campos com erros */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "Inválido",
                        (a, b) -> a
                ));

        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Dados inválidos");
        detail.setProperty("errors", errors);
        return detail;
    }

    /** Regras de negócio violadas — ex: email já cadastrado */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Requisição inválida");
        detail.setDetail(ex.getMessage());
        return detail;
    }

    /** Recurso não encontrado */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Não encontrado");
        detail.setDetail(ex.getMessage());
        return detail;
    }

    /** Credenciais incorretas no login */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        detail.setTitle("Credenciais inválidas");
        detail.setDetail("Email ou senha incorretos.");
        return detail;
    }

    /** Acesso negado — usuário autenticado mas sem permissão */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setTitle("Acesso negado");
        detail.setDetail("Você não tem permissão para acessar este recurso.");
        return detail;
    }

    /** Qualquer outra exceção não tratada */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Erro não tratado: {}", ex.getMessage(), ex);
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setTitle("Erro interno");
        detail.setDetail("Ocorreu um erro inesperado. Tente novamente.");
        return detail;
    }
}
