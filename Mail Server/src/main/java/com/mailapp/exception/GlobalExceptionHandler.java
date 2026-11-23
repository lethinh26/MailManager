package com.mailapp.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Xử lý ngoại lệ toàn cục.
 * TODO: thay bằng bản của Thịnh (M01)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        log.warn("Ngoại lệ nghiệp vụ [code={}]: {}", ex.getCode(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
            ex.getStatus().value(),
            ex.getStatus().getReasonPhrase(),
            ex.getMessage(),
            ex.getCode()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MailConnectionException.class)
    public ResponseEntity<ErrorResponse> handleMailConnectionException(MailConnectionException ex) {
        log.warn("Ngoại lệ kết nối Mail [code={}]: {}", ex.getErrorCode(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
            ex.getStatus().value(),
            ex.getStatus().getReasonPhrase(),
            ex.getMessage(),
            ex.getErrorCode()
        );
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitException(RateLimitException ex) {
        log.warn("Vượt quá giới hạn đăng nhập: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
            ex.getMessage(),
            "TOO_MANY_ATTEMPTS"
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .filter(msg -> msg != null && !msg.isBlank())
                .reduce((m1, m2) -> m1 + "; " + m2)
                .orElse("Dữ liệu yêu cầu không hợp lệ.");

        log.warn("Lỗi xác thực dữ liệu đầu vào: {}", message);
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            message,
            "VALIDATION_ERROR"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("Tài nguyên không tìm thấy: {}", ex.getResourcePath());
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            "Tài nguyên yêu cầu không tồn tại.",
            "RESOURCE_NOT_FOUND"
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(org.springframework.web.servlet.NoHandlerFoundException ex) {
        log.warn("Đường dẫn không tồn tại: {}", ex.getRequestURL());
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            "Đường dẫn yêu cầu không tồn tại.",
            "RESOURCE_NOT_FOUND"
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Truy cập bị từ chối: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            "Bạn không có quyền thực hiện thao tác này.",
            "FORBIDDEN"
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(org.springframework.security.core.AuthenticationException ex) {
        log.warn("Lỗi xác thực người dùng: {}", ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            "Yêu cầu xác thực. Vui lòng đăng nhập lại.",
            "UNAUTHORIZED"
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Lỗi máy chủ không xác định", ex);
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau.",
            "INTERNAL_SERVER_ERROR"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

