import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * No {@code @Disabled}: a skipped test must come from a platform {@code Assumptions} check. The test sources are
 * scanned for the annotation (plain or fully qualified), and the reports for any skip that no assumption explains
 * ({@code @DisabledOnOs} and the like). A skip in a file already reported for {@code @Disabled} is not repeated.
 * The report half reads whatever reports the last test run left, so run it after {@code red} or {@code green}.
 *
 * <p>Only the story's own test files are judged: those its {@code tests} phase lists under {@code artifacts} in
 * plan.json, and the skips of their classes. A file another ticket committed is not this story's defect. When the
 * plan lists no artifacts (no plan yet, an older or hand-edited one), every test file is judged, as before.
 */
final class DisabledCheck {

    /** {@code \b} after "Disabled" keeps {@code @DisabledOnOs} and friends out of the source scan. */
    private static final Pattern DISABLED = Pattern.compile("@(?:\\w+\\.)*Disabled\\b");

    private DisabledCheck() {}

    static Verdict run(Path root, String id) {
        return evaluate(id, Sources.test(root), Junit.readAll(root), Story.testsArtifacts(root, id));
    }

    static Verdict evaluate(String id, Map<String, String> testSources, List<TestResult> results) {
        return evaluate(id, testSources, results, Set.of());
    }

    /** {@code artifacts}: the tests phase's files; empty means every test file. */
    static Verdict evaluate(String id, Map<String, String> testSources, List<TestResult> results,
                            Set<String> artifacts) {
        boolean scoped = !artifacts.isEmpty();
        Map<String, String> sources = new TreeMap<>(testSources);
        if (scoped) sources.keySet().retainAll(artifacts);
        Set<String> ownClasses = new HashSet<>();
        for (String path : artifacts) ownClasses.add(simpleName(path));

        List<String> problems = new ArrayList<>();
        Set<String> flaggedClasses = new HashSet<>();
        sources.forEach((path, text) -> {
            int line = Sources.firstLine(text, DisabledCheck::disables);
            if (line > 0) {
                problems.add(path + ":" + line + ": @Disabled");
                flaggedClasses.add(simpleName(path));
            }
        });
        int assumptions = 0;
        for (TestResult r : results) {
            if (r.outcome() != TestResult.Outcome.SKIPPED) continue;
            if (scoped && !ownClasses.contains(r.outerClass())) continue;
            if (r.skippedByAssumption()) assumptions++;
            else if (!flaggedClasses.contains(r.outerClass())) {
                problems.add(r.label() + ": skipped without an Assumptions check");
            }
        }
        if (!problems.isEmpty()) return Verdict.fail("disabled", id, problems);
        return Verdict.pass("disabled", id, "no @Disabled tests"
                + (scoped ? " in the " + sources.size() + " test files of the tests phase" : "") + "; "
                + assumptions + " skipped by an assumption"
                + (results.isEmpty() ? "; no test reports found, so skips were not checked" : ""));
    }

    /** {@code a/b/FooTest.java} -> {@code FooTest}. */
    private static String simpleName(String path) {
        return path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.\\w+$", "");
    }

    /** A line of code that carries the annotation; comment lines and trailing comments do not count. */
    private static boolean disables(String line) {
        String code = line.strip();
        if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return false;
        int trailing = code.indexOf("//");
        if (trailing >= 0) code = code.substring(0, trailing);
        return DISABLED.matcher(code).find();
    }
}
