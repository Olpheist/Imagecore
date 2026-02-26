package com.green.imagecore.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    // --- 404 ---

    @Test
    void handleNotFound_Returns404WithMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found: 99");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().status());
        assertEquals("User not found: 99", response.getBody().message());
        assertEquals("/api/test", response.getBody().path());
        assertNull(response.getBody().details());
        assertNotNull(response.getBody().timestamp());
    }

    // --- 400 IllegalArgument ---

    @Test
    void handleIllegalArgument_Returns400WithMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Email already in use");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleIllegalArgument(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Email already in use", response.getBody().message());
        assertNull(response.getBody().details());
    }

    // --- 400 Validation ---

    @Test
    void handleValidation_Returns400WithFieldErrors() {
        FieldError fieldError = new FieldError("registerRequest", "email", "must not be blank");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleValidation(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation failed", response.getBody().message());
        assertNotNull(response.getBody().details());
        assertEquals(1, response.getBody().details().size());
        assertEquals("email: must not be blank", response.getBody().details().get(0));
    }

    @Test
    void handleValidation_Returns400WithMultipleFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("req", "email", "must not be blank"),
                new FieldError("req", "username", "must not be blank")
        ));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleValidation(ex, request);

        assertEquals(2, response.getBody().details().size());
    }

    // --- 400 Constraint Violation ---

    @Test
    void handleConstraintViolation_Returns400WithViolationDetails() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("register.username");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleConstraintViolation(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Constraint violation", response.getBody().message());
        assertEquals(1, response.getBody().details().size());
        assertTrue(response.getBody().details().get(0).contains("must not be blank"));
    }

    // --- 401 ---

    @Test
    void handleBadCredentials_Returns401() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleBadCredentials(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Invalid username or password", response.getBody().message());
        assertNull(response.getBody().details());
    }

    @Test
    void handleBadCredentials_DoesNotLeakCredentialDetails() {
        BadCredentialsException ex = new BadCredentialsException("User not found: john");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleBadCredentials(ex, request);

        // Original message must never be exposed
        assertNotEquals(ex.getMessage(), response.getBody().message());
        assertEquals("Invalid username or password", response.getBody().message());
    }

    // 403

    @Test
    void handleAccessDenied_Returns403() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleAccessDenied(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Access denied", response.getBody().message());
        assertNull(response.getBody().details());
    }

    // --- 500 ---

    @Test
    void handleGeneric_Returns500() {
        Exception ex = new RuntimeException("Something exploded");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleGeneric(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Unexpected error occurred", response.getBody().message());
        assertNull(response.getBody().details());
    }

    @Test
    void handleGeneric_DoesNotLeakInternalMessage() {
        Exception ex = new RuntimeException("DB connection pool exhausted");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleGeneric(ex, request);

        assertNotEquals(ex.getMessage(), response.getBody().message());
    }

    // --- ErrorResponse structure ---

    @Test
    void errorResponse_AlwaysIncludesTimestampAndPath() {
        ResourceNotFoundException ex = new ResourceNotFoundException("not found");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleNotFound(ex, request);

        assertNotNull(response.getBody().timestamp());
        assertEquals("/api/test", response.getBody().path());
        assertEquals("Not Found", response.getBody().error());
    }
}