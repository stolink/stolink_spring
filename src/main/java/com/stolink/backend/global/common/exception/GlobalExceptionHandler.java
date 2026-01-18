package com.stolink.backend.global.common.exception;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.stolink.backend.global.common.dto.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
                log.error("Access denied: {}", ex.getMessage());
                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.<Void>builder()
                                                .status(HttpStatus.FORBIDDEN)
                                                .message(ex.getMessage())
                                                .build());
        }

        @ExceptionHandler(org.springframework.security.authorization.AuthorizationDeniedException.class)
        public ResponseEntity<ApiResponse<Void>> handleAuthorizationDenied(
                        org.springframework.security.authorization.AuthorizationDeniedException ex) {
                log.error("Authorization denied: {}", ex.getMessage());
                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.<Void>builder()
                                                .status(HttpStatus.FORBIDDEN)
                                                .message("접근 권한이 없습니다 (AuthDenied).")
                                                .build());
        }

        @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
        public ResponseEntity<ApiResponse<Void>> handleSpringAccessDenied(
                        org.springframework.security.access.AccessDeniedException ex) {
                log.error("Spring Security Access denied: {}", ex.getMessage());
                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.<Void>builder()
                                                .status(HttpStatus.FORBIDDEN)
                                                .message("접근 권한이 없습니다.")
                                                .build());
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
                log.error("Resource not found: {}", ex.getMessage());
                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.<Void>builder()
                                                .status(HttpStatus.NOT_FOUND)
                                                .message(ex.getMessage())
                                                .build());
        }

        @ExceptionHandler(DuplicateRelationshipException.class)
        public ResponseEntity<ApiResponse<Void>> handleDuplicateRelationship(DuplicateRelationshipException ex) {
                log.error("Duplicate relationship: {}", ex.getMessage());
                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(ApiResponse.<Void>builder()
                                                .status(HttpStatus.CONFLICT)
                                                .message(ex.getMessage())
                                                .build());
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
                String message = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(FieldError::getDefaultMessage)
                                .collect(Collectors.joining(", "));

                log.error("Validation error: {}", message);
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.<Void>builder()
                                                .status(HttpStatus.BAD_REQUEST)
                                                .message(message)
                                                .build());
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
                log.error("Illegal argument: {}", ex.getMessage());
                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.<Void>builder()
                                                .status(HttpStatus.BAD_REQUEST)
                                                .message(ex.getMessage())
                                                .build());
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
                log.error("Internal server error", ex);
                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.<Void>builder()
                                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                .message("서버 내부 오류가 발생했습니다.")
                                                .build());
        }
}
