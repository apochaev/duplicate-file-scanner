import java.nio.file.Path;
import java.util.List;

/**
 * The gate at the end of the test-author phase: the red check, then traceability off the reports the red run just
 * produced (no second Gradle run). The first failing verdict is the one reported, so the caller still reads about
 * ten lines, and a criterion with no test stops the phase before the implementer starts.
 */
final class TestPhaseGate {

    private TestPhaseGate() {}

    static Verdict run(Path root, String id) {
        Verdict red = RedCheck.run(root, id);
        if (red.status() != Verdict.Status.PASS) return red;
        return TraceabilityCheck.run(root, id);
    }

    static Verdict evaluate(String id, Ticket ticket, List<TestResult> results, Build build, String testPlan) {
        Verdict red = RedCheck.evaluate(id, results, build);
        if (red.status() != Verdict.Status.PASS) return red;
        return TraceabilityCheck.evaluate(ticket, results, testPlan);
    }
}
