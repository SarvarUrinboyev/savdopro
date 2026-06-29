package uz.barakat.license.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Translates exceptions into consistent JSON error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return entity(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI(), null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return entity(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest req) {
        return entity(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI(), null);
    }

    /**
     * Spring Security authorization denial. A non-super-admin hitting an
     * {@code @PreAuthorize("hasRole('SUPER_ADMIN')")} endpoint throws an
     * {@link AccessDeniedException} during method invocation, which the
     * dispatcher routes here. Without this handler the catch-all below would
     * turn an ordinary 403 into a 500 — masking the real cause and looking
     * like a server bug. Map it to a clean 403 with no stack trace.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest req) {
        return entity(HttpStatus.FORBIDDEN, "Bu amal uchun ruxsatingiz yo'q",
                req.getRequestURI(), null);
    }

    /**
     * An authentication failure surfacing inside the dispatch (rare — the
     * security filter chain rejects most unauthenticated calls before they
     * reach a controller). Mapped to 401 so it never collapses into a 500.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                         HttpServletRequest req) {
        return entity(HttpStatus.UNAUTHORIZED, "Avtorizatsiya talab qilinadi",
                req.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return entity(HttpStatus.INTERNAL_SERVER_ERROR, "Server xatoligi yuz berdi",
                req.getRequestURI(), null);
    }

    /** Bean-validation failures - reports each field with its Uzbek message. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(),
                    fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Noto'g'ri qiymat");
        }
        ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(), "Ma'lumotlar noto'g'ri kiritilgan",
                pathOf(request), LocalDateTime.now(), fieldErrors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    private static ResponseEntity<ApiError> entity(
            HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
        ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, path,
                LocalDateTime.now(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

    private static String pathOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }
}
