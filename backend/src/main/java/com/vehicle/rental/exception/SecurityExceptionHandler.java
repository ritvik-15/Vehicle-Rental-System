package com.vehicle.rental.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class SecurityExceptionHandler {

    /**
     * Last-resort safety net for genuinely unexpected failures. It must
     * never disguise an unexpected error as an authentication failure:
     * genuine 401 responses are produced by the security filter chain
     * (invalid or expired JWT) and by the login endpoint's own
     * RuntimeException handling — never by this fallback. The response
     * body is a fixed message; internal exception messages, SQL
     * fragments and constraint details must never reach the client.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
