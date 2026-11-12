package com.loyalty.api;

import com.loyalty.error.ConflictException;
import com.loyalty.error.InsufficientPointsException;
import com.loyalty.error.InvalidRequestException;
import com.loyalty.error.LoyaltyException;
import com.loyalty.error.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates exceptions into HTTP responses with a consistent {@link ApiError} body. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(LoyaltyException.class)
    public ResponseEntity<ApiError> handleBusinessError(LoyaltyException e) {
        return ResponseEntity.status(statusFor(e)).body(new ApiError(e.code(), e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_REQUEST", "Request body is not valid JSON for this endpoint"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e, HttpServletRequest request) {
        // Spring's own web errors (unknown path, wrong method, unsupported media type...) carry their status.
        if (e instanceof ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            return ResponseEntity.status(status).body(new ApiError("HTTP_" + status.value(), e.getMessage()));
        }
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.internalServerError()
                .body(new ApiError("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private static HttpStatus statusFor(LoyaltyException e) {
        return switch (e) {
            case NotFoundException notFound -> HttpStatus.NOT_FOUND;
            case ConflictException conflict -> HttpStatus.CONFLICT;
            case InsufficientPointsException insufficient -> HttpStatus.UNPROCESSABLE_ENTITY;
            case InvalidRequestException invalid -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
