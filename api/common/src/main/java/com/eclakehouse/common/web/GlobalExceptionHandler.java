package com.eclakehouse.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Renders every error as RFC 9457 problem details. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    /** A downstream service answered with an error: pass 4xx through, report 5xx as a bad gateway. */
    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail handleDownstream(RestClientResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status != null && status.is4xxClientError()) {
            return ProblemDetail.forStatusAndDetail(status, "Downstream request failed: " + ex.getStatusText());
        }
        log.warn("Downstream service error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "A downstream service failed");
    }

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail handleUnreachable(ResourceAccessException ex) {
        log.warn("Downstream service unreachable", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "A downstream service is unavailable");
    }
}
