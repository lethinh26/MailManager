package com.mailapp.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.NoSuchElementException;

/**
 * Xử lý ngoại lệ toàn cục.
 * TODO: thay bằng bản của Thịnh (M01)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
        log.warn("Ngoại lệ nghiệp vụ [code={}]", ex.getCode());
        ErrorResponse body = ErrorResponse.of(
            ex.getStatus().value(),
            ex.getStatus().getReasonPhrase(),
            ex.getMessage(),
            ex.getCode(), request.getRequestURI()
        );
        return ResponseEntity.status(ex.getStatus()).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(MailConnectionException.class)
    public ResponseEntity<ErrorResponse> handleMailConnectionException(MailConnectionException ex, HttpServletRequest request) {
        log.warn("Ngoại lệ kết nối Mail [code={}]", ex.getErrorCode());
        ErrorResponse body = ErrorResponse.of(
            ex.getStatus().value(),
            ex.getStatus().getReasonPhrase(),
            ex.getMessage(),
            ex.getErrorCode(), request.getRequestURI()
        );
        return ResponseEntity.status(ex.getStatus()).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitException(RateLimitException ex, HttpServletRequest request) {
        log.warn("Vượt quá giới hạn đăng nhập");
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
            ex.getMessage(),
            "TOO_MANY_ATTEMPTS", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(org.springframework.web.bind.MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .filter(msg -> msg != null && !msg.isBlank())
                .reduce((m1, m2) -> m1 + "; " + m2)
                .orElse("Dữ liệu yêu cầu không hợp lệ.");

        log.warn("Lỗi xác thực dữ liệu đầu vào");
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            message,
            "VALIDATION_ERROR", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Tài nguyên không tìm thấy");
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            "Tài nguyên yêu cầu không tồn tại.",
            ErrorCode.NOT_FOUND.name(), request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(org.springframework.web.servlet.NoHandlerFoundException ex, HttpServletRequest request) {
        log.warn("Đường dẫn không tồn tại");
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            "Đường dẫn yêu cầu không tồn tại.",
            ErrorCode.NOT_FOUND.name(), request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Truy cập bị từ chối");
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.FORBIDDEN.value(),
            HttpStatus.FORBIDDEN.getReasonPhrase(),
            "Bạn không có quyền thực hiện thao tác này.",
            "FORBIDDEN", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
        log.warn("Lỗi xác thực người dùng");
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            "Yêu cầu xác thực. Vui lòng đăng nhập lại.",
            "UNAUTHORIZED", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu yêu cầu không hợp lệ.", request);
    }

    @ExceptionHandler({NoSuchElementException.class, EntityNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleMissingEntity(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Tài nguyên yêu cầu không tồn tại.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                String key = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT)
                        .replace("`", "").replace("'", "").replace("\"", "").trim();
                // V1 also has a legacy UNIQUE(username). On MySQL that key
                // may be reported as users.username, before the V5 index.
                // Do not parse SQL error text (it contains the rejected value).
                boolean userCreation = "POST".equalsIgnoreCase(request.getMethod())
                        && ("/api/auth/register".equals(request.getServletPath())
                        || "/api/admin/users".equals(request.getServletPath())
                        || "/api/auth/register".equals(request.getRequestURI())
                        || "/api/admin/users".equals(request.getRequestURI()));
                if (key.contains("uk_users_username_ci") || key.endsWith(".users.username")
                        || "users.username".equals(key) || (userCreation && "username".equals(key))) {
                    return error(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Tên đăng nhập đã tồn tại.", request);
                }
                if (key.contains("uk_users_email") || "users.email".equals(key)
                        || key.endsWith(".users.email") || (userCreation && "email".equals(key))) {
                    return error(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email đã tồn tại.", request);
                }
            }
            if (cause instanceof java.sql.SQLException sql
                    && ("23505".equals(sql.getSQLState()) || sql.getErrorCode() == 1062)) {
                return error(HttpStatus.CONFLICT, "CONFLICT", "Dữ liệu đã tồn tại. Vui lòng thử lại.", request);
            }
        }
        return handleGeneralException(ex, request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleAccountRevisionConflict(Exception ex, HttpServletRequest request) {
        return handleAppException(new GmailAccountChangedException(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handlePayloadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Tệp hoặc yêu cầu vượt quá kích thước cho phép.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(ex.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(405, HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
                        "Phương thức HTTP không được hỗ trợ.", "METHOD_NOT_ALLOWED", request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Định dạng dữ liệu không được hỗ trợ.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                               HttpServletRequest request) {
        // Preset a generic error representation so a second negotiation failure
        // cannot mask the original 406. Never echo Accept or OAuth parameters.
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(406, HttpStatus.NOT_ACCEPTABLE.getReasonPhrase(),
                        "Định dạng phản hồi được yêu cầu không được hỗ trợ.",
                        "NOT_ACCEPTABLE", request.getRequestURI()));
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(ErrorResponse.of(
                status.value(), status.getReasonPhrase(), message, code, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        // Keep diagnostic stack frames without logging arbitrary exception messages or request data.
        log.error("Lỗi máy chủ không xác định [{}], stack={}", ex.getClass().getName(),
                java.util.Arrays.toString(ex.getStackTrace()));
        ErrorResponse body = ErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau.",
            ErrorCode.INTERNAL_ERROR.name(), request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}

