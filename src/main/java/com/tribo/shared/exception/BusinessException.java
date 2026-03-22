package com.tribo.shared.exception;

/**
 * Exceção de regra de negócio — resulta em HTTP 400 Bad Request.
 * Use quando o erro é causado por dados inválidos do usuário.
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
