import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * No {@code @Disabled}: a skipped test must come from a platform {@code Assumptions} check. The test sources are
 * scanned for the annotation (plain or fully qualified), and the reports for any skip that no assumption explains
 * ({@code @DisabledOnOs} and the like). A skip in a file already reported for {@code @Disabled} is not repeated.
 * A {@code *.spec.ts} is scanned for Vitest's {@code it.skip}, {@code xit} and the like instead (CCW-020).
 * The report half reads whatever reports the last test run left, so run it after {@code red} or {@code green}.
 *
 * <p>Only the story's own test files are judged: those its {@code tests} phase lists under {@code artifacts} in
 * plan.json, and the skips of their classes. A file another ticket committed is not this story's defect. When the
 * plan lists no artifacts (no plan yet, an older or hand-edited one), every test file is judged, as before.
 */
final class DisabledCheck {

    /** {@code \b} after "Disabled" keeps {@code @DisabledOnOs} and friends out of the source scan. */
    private static final Pattern DISABLED = Pattern.compile("@(?:\\w+\\.)*Disabled\\b");
    /**
     * Vitest's ways to disable a test in a {@code *.spec.ts} (CCW-020): {@code it.skip}, {@code test.skip},
     * {@code describe.skip}, and {@code xit}, {@code xtest}, {@code xdescribe} called. Not {@code it.skipIf}, a
     * condition like an assumption, and not {@code foo.it.skip}.
     */
    private static final Pattern VITEST_SKIP = Pattern.compile(
            "(?<![\\w.$])((?:it|test|describe)\\.skip(?![\\w$])|x(?:it|test|describe)(?=\\s*\\())");

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

        List<String> problems = new ArrayList<>();
        Set<String> flaggedFiles = new HashSet<>();
        sources.forEach((path, text) -> {
            Pattern pattern = path.endsWith(".ts") ? VITEST_SKIP : DISABLED;
            int line = Sources.firstLine(text, l -> disables(l, pattern));
            if (line > 0) {
                problems.add(path + ":" + line + ": " + skipName(text, line, pattern));
                flaggedFiles.add(path);
            }
        });
        int assumptions = 0;
        for (TestResult r : results) {
            if (r.outcome() != TestResult.Outcome.SKIPPED) continue;
            if (scoped && artifacts.stream().noneMatch(r::fromFile)) continue;
            if (r.skippedByAssumption()) assumptions++;
            else if (flaggedFiles.stream().noneMatch(r::fromFile)) {
                problems.add(r.label() + ": skipped without an Assumptions check");
            }
        }
        if (!problems.isEmpty()) return Verdict.fail("disabled", id, problems);
        return Verdict.pass("disabled", id, "no @Disabled tests"
                + (scoped ? " in the " + sources.size() + " test files of the tests phase" : "") + "; "
                + assumptions + " skipped by an assumption"
                + (results.isEmpty() ? "; no test reports found, so skips were not checked" : ""));
    }

    /** How a problem names what disabled the file: {@code @Disabled}, or the Vitest call ({@code it.skip}, ...). */
    private static String skipName(String text, int line, Pattern pattern) {
        if (pattern == DISABLED) return "@Disabled";
        Matcher m = pattern.matcher(code(text.split("\r?\n")[line - 1]));
        return m.find() ? m.group(1) : "skip";
    }

    /** A line of code that disables a test; comment lines and trailing comments do not count. */
    private static boolean disables(String line, Pattern pattern) {
        return pattern.matcher(code(line)).find();
    }

    private static String code(String line) {
        String code = line.strip();
        if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return "";
        int trailing = code.indexOf("//");
        return trailing >= 0 ? code.substring(0, trailing) : code;
    }
}
