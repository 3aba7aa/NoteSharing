package org.example.notesharing.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Turns every failure into an RFC 7807 {@link ProblemDetail}.
 *
 * <p>Two rules hold throughout: titles are fixed strings so a client can branch on them
 * without parsing prose, and the catch-all handler logs the exception but returns no detail,
 * so connection strings, SQL and stack traces never reach a caller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(ex);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        return problem(ex);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return problem(ex);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(ex);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return problem(ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = base(HttpStatus.BAD_REQUEST, "Validation failed");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::describe)
            .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail pd = base(HttpStatus.BAD_REQUEST, "Malformed request body");
        // The parser message quotes the offending fragment, which can be a submitted value.
        pd.setDetail("The request body could not be parsed as JSON.");
        return pd;
    }

    /**
     * A path variable or request param that will not convert, such as {@code /users/abc}.
     * The client's mistake, so 400 rather than the 500 the catch-all would give.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail pd = base(HttpStatus.BAD_REQUEST, "Invalid parameter");
        pd.setDetail("Parameter '" + ex.getName() + "' has an invalid value.");
        return pd;
    }

    /** No handler matched the path. A 404, not an unknown-error 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        ProblemDetail pd = base(HttpStatus.NOT_FOUND, "Endpoint not found");
        pd.setDetail("No endpoint matches this request.");
        return pd;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail pd = base(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed");
        pd.setProperty("supportedMethods", ex.getSupportedMethods());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = base(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        pd.setDetail("An unexpected error occurred.");
        return pd;
    }

    /** Attaches the request URI so a client can correlate a failure with its call. */
    public ProblemDetail withInstance(ProblemDetail pd, URI instance) {
        pd.setInstance(instance);
        return pd;
    }

    private static Map<String, String> describe(FieldError error) {
        return Map.of(
            "field", error.getField(),
            "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
    }

    private static ProblemDetail problem(ApiException ex) {
        ProblemDetail pd = base(ex.getStatus(), ex.getTitle());
        pd.setDetail(ex.getMessage());
        return pd;
    }

    private static ProblemDetail base(HttpStatus status, String title) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        return pd;
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ErrorResponse> handleSystemException(SystemException ex) {
        ErrorEnum error = ex.getErrorEnum();
        ErrorResponse response = new ErrorResponse(
                error.getCode(),
                error.getMessage(),
                error.getHttpStatus().value(),
                Instant.now()
        );
        return ResponseEntity.status(error.getHttpStatus()).body(response);
    }
}
