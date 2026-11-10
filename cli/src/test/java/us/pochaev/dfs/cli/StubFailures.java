package us.pochaev.dfs.cli;

/**
 * Story-test helper: the CLI turns a stub's failure into an "error: scan failed" line (exit 1),
 * which a CLI-level story test would report as an assertion failure instead of the exception the
 * red gate expects. Call {@link #rethrow} on a run's output to surface the stub's own text.
 * A no-op once the feature is implemented.
 */
final class StubFailures {

    private static final String MARKER = "not implemented:";

    private StubFailures() {
    }

    /** Throws the first "not implemented:" line found in err, else in out, as an {@link UnsupportedOperationException}. */
    static void rethrow(String out, String err) {
        for (String text : new String[]{err, out}) {
            int at = text.indexOf(MARKER);
            if (at >= 0) {
                throw new UnsupportedOperationException(text.substring(at).lines().findFirst().orElseThrow());
            }
        }
    }
}
