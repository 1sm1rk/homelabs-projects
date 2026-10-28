package de.homelabs.projects.api;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import de.homelabs.projects.domain.ConflictException;
import de.homelabs.projects.domain.NotFoundException;
import de.homelabs.projects.domain.PayloadTooLargeException;
import de.homelabs.projects.domain.ShortCodeConflictException;

/**
 * The error surface of the API, one shape everywhere: {@code {"error": "..."}}.
 *
 * <ul>
 *   <li>400 — validation and field problems; the message names the offending field</li>
 *   <li>401 — no or invalid token (handled by the resource server)</li>
 *   <li>404 — unknown entity; a foreign identifier answers exactly like an absent one</li>
 *   <li>409 — archived project (read-only) or short-code collision; the collision body
 *       carries {@code suggestions} so an agent retries instead of guessing</li>
 *   <li>413 — upload above the attachment size cap</li>
 * </ul>
 */
@RestControllerAdvice
class ApiExceptionHandler {

    /** Body of every error; {@code suggestions} only on a short-code collision. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ErrorResponse(String error, List<String> suggestions) {

        ErrorResponse(String error) {
            this(error, null);
        }
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(ShortCodeConflictException.class)
    ResponseEntity<ErrorResponse> handleShortCodeConflict(ShortCodeConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(exception.getMessage(), exception.suggestions()));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ResponseEntity<ErrorResponse> handlePayloadTooLarge(PayloadTooLargeException exception) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(new ErrorResponse(exception.getMessage()));
    }

    /** The container's own multipart limit fired before the domain cap could answer. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> handleContainerUploadLimit(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse("upload exceeds the maximum request size"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError field
                        ? wireName(field.getField()) + ": " + field.getDefaultMessage()
                        : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    /** The validation messages name the WIRE field (snake_case), not the Java property. */
    private static String wireName(String field) {
        StringBuilder wire = new StringBuilder(field.length() + 4);
        for (char character : field.toCharArray()) {
            if (Character.isUpperCase(character)) {
                wire.append('_').append(Character.toLowerCase(character));
            } else {
                wire.append(character);
            }
        }
        return wire.toString();
    }

    /** Unreadable body: an unknown enum value, malformed JSON, a wrong field type. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String message = (cause == null || cause.getMessage() == null || cause.getMessage().isBlank())
                ? "invalid request body"
                : cause.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler({ MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class })
    ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(exception.getMessage()));
    }
}
