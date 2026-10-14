package dev.wordrush.common.error;

import org.springframework.http.HttpStatus;

/**
 * A business-rule failure with a stable machine-readable {@code code} (e.g. ROOM_FULL) that
 * clients switch on, and the HTTP status it maps to. Services throw these; only
 * {@link GlobalExceptionHandler} turns them into responses.
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
