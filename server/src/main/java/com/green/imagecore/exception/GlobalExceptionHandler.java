package com.green.imagecore.exception;

import com.green.imagecore.config.RequestLogFilter;
import com.green.imagecore.entities.Log;
import com.green.imagecore.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final LogService logService;

    // 404
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        String msg = "Resource not found at " + request.getRequestURI() + ": " + ex.getMessage();
        log.warn(msg);
        save("WARN", msg);

        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    // this is for static files
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        String msg = "Static resource not found at " + request.getRequestURI();
        log.warn(msg);
        save("WARN", msg);

        return build(HttpStatus.NOT_FOUND, "Not found", request.getRequestURI(), null);
    }

    // 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        String msg = "Bad request at " + request.getRequestURI() + ": " + ex.getMessage();
        log.warn(msg);
        save("WARN", msg);

        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), null);
    }

    // 400 - @Valid body validation
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.toList());

        String msg = "Validation failed at " + request.getRequestURI() + ": " + details;
        log.warn(msg);
        save("WARN", msg);

        return build(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI(), details);
    }

    // 400 - param validation
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<String> details = ex.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();

        String msg = "Constraint violation at " + request.getRequestURI() + ": " + details;
        log.warn(msg);
        save("WARN", msg);

        return build(HttpStatus.BAD_REQUEST, "Constraint violation", request.getRequestURI(), details);
    }

    // 401
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request
    ) {
        String msg = "Authentication failed at " + request.getRequestURI() + ": " + ex.getMessage();
        log.warn(msg);
        save("WARN", msg);

        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", request.getRequestURI(), null);
    }

    // 403
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        String msg = "Access denied to " + request.getRequestURI() + " from " + request.getRemoteAddr();
        log.warn(msg);
        save("WARN", msg);

        return build(HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI(), null);
    }

    // 500 - fallback
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request
    ) {
        String msg = "Unexpected error at " + request.getRequestURI() + ": " + ex.getClass().getSimpleName() + " - " + ex.getMessage();
        log.error("Unexpected error at {}", request.getRequestURI(), ex);
        save("ERROR", msg);

        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred", request.getRequestURI(), null);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String message,
            String path,
            List<String> details
    ) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                details
        );

        return ResponseEntity.status(status).body(response);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private Log buildLog(String level, String message) {
        String username = RequestLogFilter.resolveUsername();

        return Log.builder()
                .logLevel(level)
                .message(message)
                .username(username)
                .build();
    }

    private void save(String level, String message) {
        try {
            logService.save(buildLog(level, message));
        } catch (Exception ignored) {}
    }

    public record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            List<String> details
    ) {}
}