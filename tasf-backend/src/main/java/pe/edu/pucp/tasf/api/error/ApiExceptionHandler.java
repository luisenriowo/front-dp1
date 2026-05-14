package pe.edu.pucp.tasf.api.error;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.edu.pucp.tasf.service.exception.CapacityExceededException;
import pe.edu.pucp.tasf.service.exception.NoFeasibleRouteException;
import pe.edu.pucp.tasf.service.exception.ResourceNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), List.of());
    }

    @ExceptionHandler(NoFeasibleRouteException.class)
    public ResponseEntity<ApiErrorResponse> handleNoRoute(NoFeasibleRouteException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "NO_FEASIBLE_ROUTE", ex.getMessage(), List.of());
    }

    @ExceptionHandler(CapacityExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleCapacity(CapacityExceededException ex) {
        return build(HttpStatus.CONFLICT, "CAPACITY_EXCEEDED", ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult()
            .getAllErrors()
            .stream()
            .map(error -> {
                if (error instanceof FieldError fieldError) {
                    return fieldError.getField() + ": " + fieldError.getDefaultMessage();
                }
                return error.getDefaultMessage();
            })
            .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations()
            .stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage(), List.of());
    }

    private ResponseEntity<ApiErrorResponse> build(
        HttpStatus status,
        String code,
        String message,
        List<String> details
    ) {
        return ResponseEntity.status(status)
            .body(new ApiErrorResponse(code, message, details, Instant.now()));
    }
}
