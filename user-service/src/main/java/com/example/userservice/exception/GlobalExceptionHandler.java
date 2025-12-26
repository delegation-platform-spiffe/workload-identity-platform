package com.example.userservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Global exception handler to provide better error messages
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle JSON parsing errors (invalid JSON format, comments in JSON, etc.)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e) {
        log.error("Invalid request body format", e);
        
        String errorMessage = "Invalid JSON format in request body";
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null) {
            // Extract more specific error message if available
            String causeMessage = cause.getMessage();
            if (causeMessage.contains("Unexpected character")) {
                errorMessage = "Invalid JSON format: JSON does not support comments. Please remove any comments (lines starting with # or //) from your JSON.";
            } else if (causeMessage.contains("Unrecognized token")) {
                errorMessage = "Invalid JSON format: " + causeMessage;
            } else {
                errorMessage = "Invalid JSON format: " + causeMessage;
            }
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", errorMessage,
                        "message", "Please ensure your request body contains valid JSON without comments"
                ));
    }
    
    /**
     * Handle other unexpected exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "An unexpected error occurred",
                        "message", e.getMessage() != null ? e.getMessage() : "Internal server error"
                ));
    }
}

