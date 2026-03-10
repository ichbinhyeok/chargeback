package com.example.demo.dispute.api;

import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.demo.dispute.api")
public class ApiExceptionHandler {

    private static final String CASE_ACCESS_ERROR_MESSAGE =
            "Case not found or access expired. Reopen the case from its original link and try again.";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", toUserFacingMessage(ex.getMessage())));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", toUserFacingMessage(ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "request validation failed"));
    }

    private String toUserFacingMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Request failed.";
        }

        String normalized = message.toLowerCase();
        if (normalized.contains("case not found")
                || normalized.contains("invalid case token")
                || normalized.contains("missing x-case-token")) {
            return CASE_ACCESS_ERROR_MESSAGE;
        }

        return message;
    }
}

