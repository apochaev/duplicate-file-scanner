/** The check could not run (missing file, git failure, bad arguments). Becomes an ERROR verdict, exit code 2. */
final class GateError extends RuntimeException {
    GateError(String message) {
        super(message);
    }

    GateError(String message, Throwable cause) {
        super(message, cause);
    }
}
