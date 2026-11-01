package us.pochaev.dfs.server.scan;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns an {@link ApiException} into its status and an {@link ApiError} body (DFS-040). */
@RestControllerAdvice(assignableTypes = ScanController.class)
class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handle(ApiException e) {
        return ResponseEntity.status(e.status()).contentType(MediaType.APPLICATION_JSON).body(e.error());
    }
}
