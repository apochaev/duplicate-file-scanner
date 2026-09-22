import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * No {@code @Disabled}: a skipped test must come from a platform {@code Assumptions} check. The test sources are
 * scanned for the annotation (plain or fully qualified), and the reports for any skip that no assumption explains
 * ({@code @DisabledOnOs} and the like). A skip in a file already reported for {@code @Disabled} is not repeated.
 * The report half reads whatever reports the last test run left, so run it after {@code red} or {@code green}.
 */
final class DisabledCheck {

    /** {@code \b} after "Disabled" keeps {@code @DisabledOnOs} and friends out of the source scan. */
    private static final Pattern DISABLED = Pattern.compile("@(?:\\w+\\.)*Disabled\\b");

    private DisabledCheck() {}

    static Verdict run(Path root, String id) {
        return evaluate(id, Sources.test(root), Junit.readAll(root));
    }

    static Verdict evaluate(String id, Map<String, String> testSources, List<TestResult> results) {
        List<String> problems = new ArrayList<>();
        Set<String> flaggedClasses = new HashSet<>();
        testSources.forEach((path, text) -> {
            int line = Sources.firstLine(text, DisabledCheck::disables);
            if (line > 0) {
                problems.add(path + ":" + line + ": @Disabled");
                flaggedClasses.add(path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.\\w+$", ""));
            }
        });
        int assumptions = 0;
        for (TestResult r : results) {
            if (r.outcome() != TestResult.Outcome.SKIPPED) continue;
            if (r.skippedByAssumption()) assumptions++;
            else if (!flaggedClasses.contains(r.outerClass())) {
                problems.add(r.label() + ": skipped without an Assumptions check");
            }
        }
        if (!problems.isEmpty()) return Verdict.fail("disabled", id, problems);
        return Verdict.pass("disabled", id, "no @Disabled tests; " + assumptions + " skipped by an assumption"
                + (results.isEmpty() ? "; no test reports found, so skips were not checked" : ""));
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
