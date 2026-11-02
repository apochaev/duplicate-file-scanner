package us.pochaev.dfs.server.api;

import org.springframework.http.HttpStatus;

/**
 * Ends a request with an {@link ApiError} body and the given status; mapped by {@link ApiExceptionHandler}. Public
 * since DFS-047 (decision 7), so every feature package's controller can raise it.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() {
        return status;
    }

    ApiError error() {
        return new ApiError(code, getMessage());
    }

    public static ApiException unknownScan(String id) {
        return new ApiException(HttpStatus.NOT_FOUND, "UNKNOWN_SCAN", "No scan with ID " + id + " in this server run");
    }
}
