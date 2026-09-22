import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The green gate: the build and every test pass. Failures are named with a short reason, never a stack trace. */
final class GreenCheck {

    private GreenCheck() {}

    /** {@code cleanTest} first, so a compile failure cannot leave the red phase's reports to be read as fresh. */
    static Verdict run(Path root, String id) {
        Build build = Build.gradle(root, "cleanTest", "build", "--continue");
        return evaluate(id, Junit.readAll(root), build);
    }

    static Verdict evaluate(String id, List<TestResult> results, Build build) {
        List<String> problems = new ArrayList<>(build.problems());
        int passed = 0, skipped = 0;
        for (TestResult r : results) {
            switch (r.outcome()) {
                case FAILED -> problems.add(r.label() + ": " + r.reason());
                case SKIPPED -> skipped++;
                case PASSED -> passed++;
            }
        }
        if (problems.isEmpty() && build.exit() != 0) {
            problems.add("build failed (exit " + build.exit() + ")"
                    + (build.wentWrong().isEmpty() ? "" : ": " + build.wentWrong()));
        }
        if (problems.isEmpty() && results.isEmpty()) problems.add("no test reports found");
        if (!problems.isEmpty()) return Verdict.fail("green", id, problems);
        return Verdict.pass("green", id, "build and tests green: " + passed + " passed"
                + (skipped > 0 ? ", " + skipped + " skipped" : ""));
    }
}
