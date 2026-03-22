package com.tribo.shared.exception;

/**
 * Exceção de recurso não encontrado — resulta em HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
