import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The red gate. Every story test (display name starting with {@code <ID>.AC-}) must fail with a stub's
 * {@code UnsupportedOperationException("not implemented: ...")}. Every other test must pass, with two exceptions
 * (CCW-015): one that fails on this ticket's own stub ({@code not implemented: <ID>}, or that as its root cause), and
 * one in a test class the architect declared under {@code design.md}'s {@code ## Expected breakage}.
 */
final class RedCheck {

    static final String BREAKAGE_HEADING = "## Expected breakage";
    private static final Pattern DECLARATION = Pattern.compile("^- `([A-Za-z_$][A-Za-z0-9_$]*)`");

    private RedCheck() {}

    /** Runs the whole suite from a clean slate (so stale reports cannot be mistaken for fresh ones) and judges it. */
    static Verdict run(Path root, String id) {
        Build build = Build.gradle(root, "cleanTest", "test", "--continue");
        return evaluate(id, Junit.readAll(root), build, declaredClasses(designText(root, id)));
    }

    static Verdict evaluate(String id, List<TestResult> results, Build build) {
        return evaluate(id, results, build, Set.of());
    }

    static Verdict evaluate(String id, List<TestResult> results, Build build, Set<String> declared) {
        List<String> problems = new ArrayList<>(build.problems());
        if (results.isEmpty()) {
            problems.add(build.exit() == 0 ? "no test reports found"
                    : "no tests ran; the build failed" + (build.wentWrong().isEmpty() ? "" : ": " + build.wentWrong()));
            return Verdict.fail("red", id, problems);
        }

        String storyPrefix = id + ".AC-";
        int story = 0, ownStub = 0, inDeclared = 0, other = 0, skipped = 0;
        for (TestResult r : results) {
            if (r.name().startsWith(storyPrefix)) {
                story++;
                storyProblem(r).ifPresent(problems::add);
            } else if (r.outcome() == TestResult.Outcome.FAILED) {
                if (r.failedWithStubOf(id)) ownStub++;
                else if (declared.contains(r.outerClass())) inDeclared++;
                else problems.add("regression: " + r.label() + " failed (" + r.reason() + ")");
            } else {
                other++;
                if (r.outcome() == TestResult.Outcome.SKIPPED) skipped++;
            }
        }
        if (story == 0) problems.add("no story tests found (display names starting with " + storyPrefix + ")");

        if (!problems.isEmpty()) return Verdict.fail("red", id, problems);
        return Verdict.pass("red", id, story + " story tests fail with stubs, " + ownStub
                + " other tests fail on the ticket's stub, " + inDeclared + " fail in declared classes, " + other
                + " other tests pass" + (skipped > 0 ? " (" + skipped + " skipped)" : ""));
    }

    /**
     * The simple class names declared as bullets {@code - `Name`: reason} directly under the heading
     * {@code ## Expected breakage}, up to the next heading. No design, no such heading, or "None." declares nothing:
     * a missing or misspelled section can only make the gate stricter.
     */
    static Set<String> declaredClasses(String design) {
        Set<String> names = new LinkedHashSet<>();
        if (design == null) return names;
        boolean inSection = false;
        for (String line : design.lines().toList()) {
            if (line.startsWith("#")) {
                inSection = line.strip().equals(BREAKAGE_HEADING);
                continue;
            }
            if (!inSection) continue;
            Matcher m = DECLARATION.matcher(line);
            if (m.find()) names.add(m.group(1));
        }
        return names;
    }

    /** The story's design.md, or null when there is none or the story cannot be found (nothing is declared). */
    private static String designText(Path root, String id) {
        try {
            return Story.find(root, id).designText();
        } catch (GateError e) {
            return null;
        }
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
