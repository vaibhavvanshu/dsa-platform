package com.capstone.dsaplatform.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns exceptions into the contract's error body, so every 400/404 looks the same
 * to the frontend no matter which layer detected the problem.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(String error, String message) {
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> badRequest(BadRequestException ex) {
        return badRequest(ex.getMessage());
    }

    // e.g. GET /api/topics/graph without ?userId
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParam(MissingServletRequestParameterException ex) {
        return badRequest("Query parameter '" + ex.getParameterName() + "' is required");
    }

    // e.g. ?difficulty=VERY_HARD or ?limit=abc
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'");
    }

    // Missing or malformed JSON body.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex) {
        return badRequest("Request body is missing or is not valid JSON");
    }

    private ResponseEntity<ApiError> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("BAD_REQUEST", message));
    }
}
