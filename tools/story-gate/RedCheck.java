import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The red gate. Every story test (display name starting with {@code <ID>.AC-}) must fail with a stub's
 * {@code UnsupportedOperationException("not implemented: ...")}; every other test must pass.
 */
final class RedCheck {

    private RedCheck() {}

    /** Runs the whole suite from a clean slate (so stale reports cannot be mistaken for fresh ones) and judges it. */
    static Verdict run(Path root, String id) {
        Build build = Build.gradle(root, "cleanTest", "test", "--continue");
        return evaluate(id, Junit.readAll(root), build);
    }

    static Verdict evaluate(String id, List<TestResult> results, Build build) {
        List<String> problems = new ArrayList<>(build.problems());
        if (results.isEmpty()) {
            problems.add(build.exit() == 0 ? "no test reports found"
                    : "no tests ran; the build failed" + (build.wentWrong().isEmpty() ? "" : ": " + build.wentWrong()));
            return Verdict.fail("red", id, problems);
        }

        String storyPrefix = id + ".AC-";
        int story = 0, other = 0, skipped = 0;
        for (TestResult r : results) {
            if (r.name().startsWith(storyPrefix)) {
                story++;
                storyProblem(r).ifPresent(problems::add);
            } else if (r.outcome() == TestResult.Outcome.FAILED) {
                problems.add("regression: " + r.label() + " failed (" + r.reason() + ")");
            } else {
                other++;
                if (r.outcome() == TestResult.Outcome.SKIPPED) skipped++;
            }
        }
        if (story == 0) problems.add("no story tests found (display names starting with " + storyPrefix + ")");

        if (!problems.isEmpty()) return Verdict.fail("red", id, problems);
        return Verdict.pass("red", id, story + " story tests fail with stubs, " + other + " other tests pass"
                + (skipped > 0 ? " (" + skipped + " skipped)" : ""));
    }

    private static Optional<String> storyProblem(TestResult r) {
        return switch (r.outcome()) {
            case PASSED -> Optional.of(r.label() + ": passed against stubs");
            case SKIPPED -> Optional.of(r.label() + ": skipped; a story test must fail against stubs");
            case FAILED -> {
                if (r.failedWithStub()) yield Optional.empty();
                if (TestResult.STUB_EXCEPTION.equals(r.failureType())) {
                    yield Optional.of(r.label() + ": did not fail from a stub (" + r.reason() + ")");
                }
                yield Optional.of(r.label() + ": failed with " + r.reason());
            }
        };
    }
}
