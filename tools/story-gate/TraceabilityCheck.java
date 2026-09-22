import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The chain criterion, scenario, test, matched by ID. Every live criterion has a tagged scenario, a test whose display
 * name starts with {@code <TICKET-ID>.AC-<n>}, and a mention in test-plan.md; every scenario is tagged with existing,
 * live criteria; no test names a retired or unknown criterion. Retitling a scenario never breaks the link.
 * It reads the reports the red or green gate just produced, and runs nothing itself.
 */
final class TraceabilityCheck {

    private TraceabilityCheck() {}

    static Verdict run(Path root, String id) {
        Story story = Story.find(root, id);
        return evaluate(story.ticket(), Junit.readAll(root), story.testPlanText());
    }

    static Verdict evaluate(Ticket ticket, List<TestResult> results, String testPlan) {
        String id = ticket.id();
        List<String> problems = new ArrayList<>();
        List<Ticket.Criterion> live = ticket.liveCriteria();
        if (live.isEmpty()) problems.add("the ticket has no live acceptance criteria");

        Set<Integer> scenarioTagged = new LinkedHashSet<>();
        for (Ticket.Scenario s : ticket.scenarios()) {
            if (s.tags().isEmpty()) problems.add("scenario \"" + s.title() + "\": no @AC tag");
            for (int n : s.tags()) {
                Ticket.Criterion c = ticket.criterion(n);
                if (c == null) problems.add("scenario \"" + s.title() + "\": @AC-" + n + " does not exist");
                else if (c.retired()) problems.add("scenario \"" + s.title() + "\": @AC-" + n + " is retired");
                else scenarioTagged.add(n);
            }
        }

        Set<Integer> tested = new LinkedHashSet<>();
        for (TestResult r : results) {
            for (int n : criteriaNamed(id, r.name())) {
                Ticket.Criterion c = ticket.criterion(n);
                if (c == null) problems.add("test \"" + r.name() + "\": names " + id + ".AC-" + n + ", which does not exist");
                else if (c.retired()) problems.add("test \"" + r.name() + "\": names retired " + id + ".AC-" + n);
                else tested.add(n);
            }
        }

        if (testPlan == null) problems.add("test-plan.md not found");
        for (Ticket.Criterion c : live) {
            String full = id + ".AC-" + c.number();
            if (!scenarioTagged.contains(c.number())) problems.add("AC-" + c.number() + ": no scenario is tagged with it");
            if (!tested.contains(c.number())) {
                problems.add("AC-" + c.number() + ": no test display name starts with " + full);
            }
            if (testPlan != null && !mentions(testPlan, full)) {
                problems.add("AC-" + c.number() + ": " + full + " not found in test-plan.md");
            }
        }

        if (!problems.isEmpty()) return Verdict.fail("traceability", id, problems);
        return Verdict.pass("traceability", id, live.size() + " criteria traced through "
                + ticket.scenarios().size() + " scenarios to tests and test-plan.md");
    }

    /**
     * The criterion numbers a display name claims: the run of IDs at its very start, separated by spaces, commas,
     * plus signs or ampersands ({@code DFS-001.AC-3, DFS-001.AC-4 Title}). IDs of other tickets are not this
     * ticket's business and stop the run.
     */
    static List<Integer> criteriaNamed(String id, String displayName) {
        List<Integer> numbers = new ArrayList<>();
        String prefix = id + ".AC-";
        int pos = 0;
        while (displayName.startsWith(prefix, pos)) {
            int digits = pos + prefix.length(), end = digits;
            while (end < displayName.length() && Character.isDigit(displayName.charAt(end))) end++;
            if (end == digits) break;
            numbers.add(Integer.parseInt(displayName.substring(digits, end)));
            pos = end;
            while (pos < displayName.length() && " \t,;+&".indexOf(displayName.charAt(pos)) >= 0) pos++;
        }
        return numbers;
    }

    private static boolean mentions(String text, String fullId) {
        return Pattern.compile("(?<![\\w.-])" + Pattern.quote(fullId) + "(?!\\d)").matcher(text).find();
    }
}
