package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;
import java.util.List;

/**
 * How one {@link Operation.Quarantine} of an executed plan ended (DFS-051 decisions 4, 7b, 8). Every link of the
 * operation appears exactly once, as a {@link MovedFile}, a {@link SkippedFile} or a not-attempted path.
 */
public sealed interface OperationOutcome {

    /** The operation's set digest. */
    String digest();

    /** The operation's set size in bytes. */
    long size();

    /** The operation's keeper, never moved. */
    Path keeper();

    /** Every link moved into quarantine. */
    record Moved(String digest, long size, Path keeper, List<MovedFile> moved) implements OperationOutcome {

        public Moved {
            java.util.Objects.requireNonNull(digest, "digest");
            java.util.Objects.requireNonNull(keeper, "keeper");
            java.util.Objects.requireNonNull(moved, "moved");
            moved = List.copyOf(moved);
            if (moved.isEmpty()) {
                throw new IllegalArgumentException("moved must not be empty");
            }
        }
    }

    /** Some links moved and the rest were skipped (decision 8); both lists in the operation's link order. */
    record PartlyMoved(String digest, long size, Path keeper, List<MovedFile> moved, List<SkippedFile> skipped)
            implements OperationOutcome {

        public PartlyMoved {
            java.util.Objects.requireNonNull(digest, "digest");
            java.util.Objects.requireNonNull(keeper, "keeper");
            java.util.Objects.requireNonNull(moved, "moved");
            java.util.Objects.requireNonNull(skipped, "skipped");
            moved = List.copyOf(moved);
            skipped = List.copyOf(skipped);
            if (moved.isEmpty()) {
                throw new IllegalArgumentException("moved must not be empty");
            }
            if (skipped.isEmpty()) {
                throw new IllegalArgumentException("skipped must not be empty");
            }
        }
    }

    /** No link moved; each was skipped for its own reason. */
    record Skipped(String digest, long size, Path keeper, List<SkippedFile> skipped) implements OperationOutcome {

        public Skipped {
            java.util.Objects.requireNonNull(digest, "digest");
            java.util.Objects.requireNonNull(keeper, "keeper");
            java.util.Objects.requireNonNull(skipped, "skipped");
            skipped = List.copyOf(skipped);
            if (skipped.isEmpty()) {
                throw new IllegalArgumentException("skipped must not be empty");
            }
        }
    }

    /** The run was cancelled before this operation started; nothing of it was checked, logged or moved. */
    record NotAttempted(String digest, long size, Path keeper, List<Path> links) implements OperationOutcome {

        public NotAttempted {
            java.util.Objects.requireNonNull(digest, "digest");
            java.util.Objects.requireNonNull(keeper, "keeper");
            java.util.Objects.requireNonNull(links, "links");
            links = List.copyOf(links);
            if (links.isEmpty()) {
                throw new IllegalArgumentException("links must not be empty");
            }
        }
    }
}
