package us.pochaev.dfs.server.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns an {@link ApiException} into its status and an {@link ApiError} body (DFS-040). Covers every controller under
 * {@code us.pochaev.dfs.server} since DFS-047 (decision 7), so the scan and result packages, and any later feature
 * package, need no change here; only the API controllers raise {@link ApiException}.
 */
@RestControllerAdvice(basePackages = "us.pochaev.dfs.server")
class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handle(ApiException e) {
        return ResponseEntity.status(e.status()).contentType(MediaType.APPLICATION_JSON).body(e.error());
    }
}
