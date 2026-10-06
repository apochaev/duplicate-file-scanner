package us.pochaev.dfs.scanner.domain.model;

/**
 * The outcome of a use-case call (DFS-018 decision 4): an expected outcome is a value, never an
 * exception. It describes the call, not the work: a scan that ran and failed is a {@link Success}
 * whose snapshot says FAILED. A {@code null} argument stays a {@link NullPointerException}.
 *
 * @param <T> the value type of a successful call
 */
public sealed interface Result<T> {

    /** The call succeeded; {@code value} is never null. */
    record Success<T>(T value) implements Result<T> {
    }

    /** The call was refused for the reason {@code code}; {@code message} is never null. */
    record Failure<T>(ErrorCode code, String message) implements Result<T> {
    }
}
