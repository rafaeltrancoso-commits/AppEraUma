package com.rrsistemas.erauma.shared;

import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        List<FieldErrorItem> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorItem(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError(Instant.now(), 400, "VALIDATION_ERROR", "Dados inválidos", errors));
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiError.of(exception.getStatus().value(), exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({BadCredentialsException.class})
    ResponseEntity<ApiError> badCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(401, "AUTHENTICATION_INVALID", "Email ou senha inválidos"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(403, "ACCESS_DENIED", "Acesso negado"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409, "DATA_CONFLICT", "Registro já existente ou inválido"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableRequest() {
        return ResponseEntity.badRequest().body(ApiError.of(400, "INVALID_REQUEST", "Requisição inválida"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingRequestParameter(MissingServletRequestParameterException exception) {
        if ("files".equals(exception.getParameterName())) {
            return ResponseEntity.badRequest().body(ApiError.of(400, "PHOTO_REQUIRED", "Informe ao menos uma foto"));
        }
        return ResponseEntity.badRequest().body(ApiError.of(400, "INVALID_REQUEST", "RequisiÃ§Ã£o invÃ¡lida"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> missingRequestPart(MissingServletRequestPartException exception) {
        if ("files".equals(exception.getRequestPartName())) {
            return ResponseEntity.badRequest().body(ApiError.of(400, "PHOTO_REQUIRED", "Informe ao menos uma foto"));
        }
        return ResponseEntity.badRequest().body(ApiError.of(400, "INVALID_REQUEST", "RequisiÃ§Ã£o invÃ¡lida"));
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiError> multipart() {
        return ResponseEntity.badRequest().body(ApiError.of(400, "INVALID_MULTIPART", "NÃ£o foi possÃ­vel processar o envio da foto"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException exception) {
        Throwable cause = rootCause(exception);
        if (cause instanceof FileSizeLimitExceededException) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiError.of(413, "PHOTO_TOO_LARGE", "A foto deve ter no máximo 10 MB."));
        }
        if (cause instanceof SizeLimitExceededException) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiError.of(413, "UPLOAD_TOO_LARGE", "O conjunto de fotos excede o limite permitido."));
        }
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of(413, "UPLOAD_TOO_LARGE", "O conjunto de fotos excede o limite permitido."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        LOGGER.error("Unexpected API error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(500, "UNEXPECTED_ERROR", "Erro inesperado"));
    }
    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
