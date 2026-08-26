package com.veriprotocol.springAI.core;

import java.net.ConnectException;
import java.sql.SQLTransientConnectionException;
import java.util.Map;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
	
	private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);


    
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(Exception.class)
    public ErrorResponse handle(Exception e, HttpServletRequest request) {
        Throwable root = rootCause(e);
        
     // ✅ Always log the exception (stack trace)
        log.error("Unhandled exception path={} method={} root={}",
                request.getRequestURI(),
                request.getMethod(),
                root == null ? "null" : root.getClass().getName(),
                e);

        if (isShardUnavailable(root)) {
            return new ErrorResponse(
                    "SHARD_UNAVAILABLE",
                    "No writable database node is available for this shard"
            );
        }

        if (isDbDown(root)) {
            return new ErrorResponse(
                    "DB_UNAVAILABLE",
                    "Database is unavailable. Please retry."
            );
        }

        return new ErrorResponse(
                "INTERNAL_ERROR",
                "Internal server error"
        );
    }

    private boolean isDbDown(Throwable t) {
        if (t == null) return false;

        if (t instanceof ConnectException) return true;
        if (t instanceof SQLTransientConnectionException) return true;

        String msg = t.getMessage();
        return msg != null && (
                msg.contains("Connection refused") ||
                msg.contains("Failed to obtain JDBC Connection") ||
                msg.contains("Connection is not available")
        );
    }

    private boolean isShardUnavailable(Throwable t) {

        if (t == null) {
            return false;
        }

        String msg = t.getMessage();

        return msg != null &&
                msg.contains("No writable primary available");
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur;
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<?> handleIdempotencyConflict(
            IdempotencyConflictException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "code", "IDEMPOTENCY_CONFLICT",
                        "message", ex.getMessage()
                ));
    }
    public record ErrorResponse(String code, String message) {}
}
