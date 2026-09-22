import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Test freeze: no test file has been added, changed or deleted since the commit the {@code tests} phase recorded in
 * plan.json, so the implementer cannot turn the tests green by editing them.
 */
final class FreezeCheck {

    private FreezeCheck() {}

    static Verdict run(Path root, String id) {
        return run(root, id, Story.find(root, id).planText());
    }

    /** The commit comes from the plan, never from an argument. */
    static Verdict run(Path root, String id, String planText) {
        if (planText == null) return Verdict.fail("freeze", id, List.of("plan.json not found"));
        Object plan;
        try {
            plan = Json.parse(planText);
        } catch (Json.ParseException e) {
            return Verdict.fail("freeze", id, List.of("plan.json does not parse: " + e.getMessage()));
        }
        String commit = Story.testsCommit(plan);
        if (commit == null) {
            return Verdict.fail("freeze", id, List.of("plan.json records no commit for the tests phase"));
        }
        return evaluate(id, commit, Git.changesSince(root, commit));
    }

    static Verdict evaluate(String id, String commit, List<Git.Change> changes) {
        List<String> problems = new ArrayList<>();
        for (Git.Change c : changes) {
            if (Sources.isTestPath(c.path())) problems.add(c.path() + ": " + c.status() + " since the test phase");
        }
        String shortCommit = commit.length() > 8 ? commit.substring(0, 8) : commit;
        if (!problems.isEmpty()) return Verdict.fail("freeze", id, problems);
        return Verdict.pass("freeze", id, "no test file changed since " + shortCommit);
    }
}
