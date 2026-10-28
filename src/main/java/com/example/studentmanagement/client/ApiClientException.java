package com.example.studentmanagement.client;

import com.example.studentmanagement.exception.ApiError;
import org.springframework.http.HttpStatusCode;

import java.util.Map;

/**
 * Thrown by {@link StudentManagementClient} when the API answers with a 4xx or 5xx status.
 *
 * <p>Carries the HTTP status and the API's own error body ({@link ApiError}), so callers can show the server's
 * message or per-field validation errors instead of a generic "request failed".
 */
public class ApiClientException extends RuntimeException {

    private final HttpStatusCode status;
    private final ApiError error;

    public ApiClientException(HttpStatusCode status, ApiError error, Throwable cause) {
        super(error != null && error.message() != null ? error.message() : "Request failed with status " + status,
                cause);
        this.status = status;
        this.error = error;
    }

    /** The HTTP status the API returned, e.g. 404 or 409. */
    public HttpStatusCode getStatus() {
        return status;
    }

    /** The API's error body, or {@code null} if the response had no readable JSON body. */
    public ApiError getError() {
        return error;
    }

    /** Validation errors by field name (e.g. {@code email -> "Email is required"}); empty if there are none. */
    public Map<String, String> getFieldErrors() {
        return error != null && error.fieldErrors() != null ? error.fieldErrors() : Map.of();
    }
}
