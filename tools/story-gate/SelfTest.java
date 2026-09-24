import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * {@code selftest}: runs every check on the small fixture cases under {@code tools/story-gate/selftest/cases} and
 * compares each verdict with the case's {@code expected.txt}. A case is a folder named after the ticket and criterion
 * it proves ({@code CCW-009.AC-7-...}, or {@code AC-7-...} for the {@link #DEFAULT_TICKET} the harness was built
 * under), holding {@code case.json} (see {@link SelfTestCase}) and {@code expected.txt}. Every scenario of every
 * ticket that has a case must be claimed by a case of that ticket, so a scenario cannot lose its evidence unnoticed.
 *
 * <p>The orchestrator runs it as a pre-flight before trusting the gates. {@code --bless} writes a missing
 * {@code expected.txt} from the actual verdict: review the file before keeping it.
 */
final class SelfTest {

    /** The ticket an unprefixed case belongs to: the one that built this harness. */
    static final String DEFAULT_TICKET = "CCW-003";

    /** Group 1: the optional ticket prefix; group 2: the criterion number. */
    private static final Pattern CASE_NAME = Pattern.compile("^(?:([A-Z]+-\\d{3})\\.)?AC-(\\d+)-.+");

    private SelfTest() {}

    static Verdict run(Path root, boolean bless) {
        Path selftest = root.resolve("tools").resolve("story-gate").resolve("selftest");
        Path casesDir = selftest.resolve("cases");
        if (!Files.isDirectory(casesDir)) throw new GateError("no self-test cases at " + casesDir);

        List<String> problems = new ArrayList<>();
        // ticket -> scenario title -> case names
        Map<String, Map<String, Set<String>>> claimed = new TreeMap<>();
        int cases = 0;
        for (Path dir : caseDirs(casesDir)) {
            cases++;
            problems.addAll(runCase(dir, selftest.resolve("shared"), root, bless, claimed));
        }
        cases++;
        problems.addAll(JsonSelfTest.run());
        problems.addAll(coverage(root, claimed));

        if (!problems.isEmpty()) return Verdict.fail("selftest", "", problems);
        return Verdict.pass("selftest", "", cases + " cases match their expected verdicts; every scenario of "
                + String.join(", ", claimed.keySet()) + " is covered");
    }

    /** The ticket a case folder belongs to, from its prefix; null when the name is malformed. */
    static String ticketOf(String caseName) {
        Matcher m = CASE_NAME.matcher(caseName);
        if (!m.matches()) return null;
        return m.group(1) == null ? DEFAULT_TICKET : m.group(1);
    }

    private static List<String> runCase(Path dir, Path shared, Path root, boolean bless,
                                        Map<String, Map<String, Set<String>>> claimed) {
        String name = dir.getFileName().toString();
        List<String> problems = new ArrayList<>();
        String ticket = ticketOf(name);
        if (ticket == null) {
            problems.add(name + ": a case is named after its criterion, like AC-7-some-title or CCW-009.AC-7-some-title");
        }
        Map<String, Object> spec;
        try {
            spec = Json.asObject(Json.parse(Files.readString(dir.resolve("case.json"))));
        } catch (IOException | Json.ParseException e) {
            return List.of(name + ": case.json unreadable: " + e.getMessage());
        }
        if (spec == null) return List.of(name + ": case.json is not an object");
        if (ticket != null) {
            Map<String, Set<String>> titles = claimed.computeIfAbsent(ticket, k -> new LinkedHashMap<>());
            for (Object title : SelfTestCase.list(spec, "scenarios")) {
                titles.computeIfAbsent(String.valueOf(title), k -> new HashSet<>()).add(name);
            }
        }

        String actual;
        try {
            actual = new SelfTestCase(dir, shared, root, spec).execute();
        } catch (RuntimeException e) { // includes GateError
            return List.of(name + ": case failed to run: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        Path expectedFile = dir.resolve("expected.txt");
        try {
            if (!Files.isRegularFile(expectedFile)) {
                if (!bless) {
                    problems.add(name + ": expected.txt is missing (run selftest --bless, then review it)");
                    return problems;
                }
                if (actual.contains("MISMATCH:")) { // never bless a disagreement between the two launchers
                    problems.add(name + ": not blessed, the output reports a mismatch: " + actual.strip());
                    return problems;
                }
                Files.writeString(expectedFile, actual);
                return problems;
            }
            String difference = firstDifference(Files.readString(expectedFile).replace("\r\n", "\n"), actual);
            if (difference != null) problems.add(name + ": " + difference);
        } catch (IOException e) {
            problems.add(name + ": " + e.getMessage());
        }
        return problems;
    }

    /** null when equal, else the first differing line, short. */
    static String firstDifference(String expected, String actual) {
        if (expected.equals(actual)) return null;
        String[] e = expected.split("\n", -1), a = actual.split("\n", -1);
        for (int i = 0; i < Math.max(e.length, a.length); i++) {
            String el = i < e.length ? e[i] : "<no line>", al = i < a.length ? a[i] : "<no line>";
            if (!el.equals(al)) return "line " + (i + 1) + " expected \"" + el + "\" but got \"" + al + "\"";
        }
        return "outputs differ";
    }

    /**
     * For each ticket that has a case: each of its scenarios needs one of its cases named after one of the
     * scenario's criteria, and each claim must name a real scenario of that ticket. A case is never checked against
     * another ticket's scenarios.
     */
    private static List<String> coverage(Path root, Map<String, Map<String, Set<String>>> claimed) {
        List<String> problems = new ArrayList<>();
        boolean several = claimed.size() > 1;
        claimed.forEach((id, titles) -> {
            String of = several ? id + " " : ""; // one ticket reads as before
            Ticket ticket = Story.find(root, id).ticket();
            Set<String> known = new HashSet<>();
            for (Ticket.Scenario s : ticket.scenarios()) {
                known.add(s.title());
                Set<String> cases = titles.getOrDefault(s.title(), Set.of());
                boolean covered = cases.stream().anyMatch(c -> {
                    Matcher m = CASE_NAME.matcher(c);
                    return m.matches() && s.tags().contains(Integer.parseInt(m.group(2)));
                });
                if (!covered) problems.add(of + "scenario \"" + s.title() + "\" has no case named after its criterion");
            }
            titles.forEach((title, cases) -> {
                if (!known.contains(title)) {
                    problems.add("cases " + cases + " name an unknown " + of + "scenario \"" + title + "\"");
                }
            });
        });
        return problems;
    }

    private static List<Path> caseDirs(Path casesDir) {
        try (Stream<Path> dirs = Files.list(casesDir)) {
            return dirs.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            throw new GateError("cannot list " + casesDir + ": " + e.getMessage(), e);
        }
    }
}
