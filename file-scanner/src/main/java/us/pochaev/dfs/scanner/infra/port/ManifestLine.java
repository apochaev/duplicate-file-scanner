package us.pochaev.dfs.scanner.infra.port;

import us.pochaev.dfs.scanner.domain.model.JobStatus;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.domain.model.SkipCause;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * One line of a plan execution's manifest (DFS-051 decision 6), in the order a run writes them: one {@link Run}; per
 * attempted link a {@link Moving} then a {@link Moved} or {@link Skipped}; one {@link End}. The JSON field names are
 * the story's {@code design.md}'s; readers ignore fields and line types they do not know (the DFS-030 rule).
 */
public sealed interface ManifestLine {

    /** {@code {"type":"run","runId","planId","roots":[...],"started"}}. */
    record Run(PlanExecutionId runId, String planId, List<Path> roots, Instant started) implements ManifestLine {

        public Run {
        }
    }

    /**
     * {@code {"type":"moving","path","quarantine","keeper","digest","size","modified"}}: written and forced to disk
     * before the link is checked and renamed. {@code modified} is the scan's last-modified time of {@code path}.
     */
    record Moving(Path path, Path quarantine, Path keeper, String digest, long size, Instant modified)
            implements ManifestLine {

        public Moving {
        }
    }

    /** {@code {"type":"moved","path","quarantine"}}: the rename succeeded. */
    record Moved(Path path, Path quarantine) implements ManifestLine {

        public Moved {
        }
    }

    /** {@code {"type":"skipped","path","reason","subject","detail"}}: {@code reason} is the {@link SkipCause}'s name. */
    record Skipped(Path path, SkipCause cause, Path subject, String detail) implements ManifestLine {

        public Skipped {
        }
    }

    /**
     * {@code {"type":"end","status","moved","skipped","notAttempted","finished"}}: {@code status} is COMPLETED,
     * CANCELLED or FAILED as the run itself saw it; the counts are paths.
     */
    record End(JobStatus status, int moved, int skipped, int notAttempted, Instant finished) implements ManifestLine {

        public End {
        }
    }
}
