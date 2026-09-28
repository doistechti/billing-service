package com.doistech.billingservice.shared;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiErrorResponse> handleBusiness(BusinessRuleException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), List.of());
    }

    @ExceptionHandler(GatewayIntegrationException.class)
    ResponseEntity<ApiErrorResponse> handleGateway(GatewayIntegrationException exception) {
        HttpStatus status = exception.isTransient() ? HttpStatus.BAD_GATEWAY : HttpStatus.BAD_REQUEST;
        return build(status, exception.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(ConflictException exception) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException exception) {
        log.warn("data integrity violation: {}", exception.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "resource already exists", List.of());
    }

    @ExceptionHandler(InvalidSignatureException.class)
    ResponseEntity<ApiErrorResponse> handleSignature(InvalidSignatureException exception) {
        return build(HttpStatus.UNAUTHORIZED, exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "validation failed", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return build(HttpStatus.BAD_REQUEST, "malformed request body", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleGeneric(Exception exception) {
        log.error("unexpected error", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal error", List.of());
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                details
        ));
    }
}
