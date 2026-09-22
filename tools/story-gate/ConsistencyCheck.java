import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * plan.json parses and holds together, and the ticket's status agrees with the plan's state. The pairs that agree:
 * running with in-progress; awaiting-approval with in-progress or in-review (the last gate comes after
 * {@code tickets review}); blocked with blocked; done with in-review or done.
 */
final class ConsistencyCheck {

    private static final Map<String, Set<String>> STATUS_FOR_STATE = Map.of(
            "running", Set.of("in-progress"),
            "awaiting-approval", Set.of("in-progress", "in-review"),
            "blocked", Set.of("blocked"),
            "done", Set.of("in-review", "done"));
    private static final Set<String> PHASE_STATUSES = Set.of("pending", "in-progress", "done");

    private ConsistencyCheck() {}

    static Verdict run(Path root, String id) {
        Story story = Story.find(root, id);
        return evaluate(id, story.ticket().status(), story.planText());
    }

    static Verdict evaluate(String id, String ticketStatus, String planText) {
        if (planText == null) return Verdict.fail("consistency", id, List.of("plan.json not found"));
        Object parsed;
        try {
            parsed = Json.parse(planText);
        } catch (Json.ParseException e) {
            return Verdict.fail("consistency", id, List.of("plan.json does not parse: " + e.getMessage()));
        }
        Map<String, Object> plan = Json.asObject(parsed);
        if (plan == null) return Verdict.fail("consistency", id, List.of("plan.json is not a JSON object"));

        List<String> problems = new ArrayList<>();
        if (!id.equals(plan.get("ticket"))) {
            problems.add("plan.json ticket is " + plan.get("ticket") + ", expected " + id);
        }
        String state = Json.asString(plan.get("state"));
        if (state == null || !STATUS_FOR_STATE.containsKey(state)) {
            problems.add("plan.json state is " + state + ", expected one of " + new TreeSet<>(STATUS_FOR_STATE.keySet()));
        } else if (!STATUS_FOR_STATE.get(state).contains(ticketStatus)) {
            problems.add("ticket status is " + ticketStatus + " but plan state is " + state
                    + " (expected " + new TreeSet<>(STATUS_FOR_STATE.get(state)) + ")");
        }
        phaseProblems(plan, problems);

        if (!problems.isEmpty()) return Verdict.fail("consistency", id, problems);
        return Verdict.pass("consistency", id, "plan.json parses; state " + state + " agrees with ticket status "
                + ticketStatus);
    }

    private static void phaseProblems(Map<String, Object> plan, List<String> problems) {
        List<Object> phases = Json.asArray(plan.get("phases"));
        if (phases == null) {
            problems.add("plan.json has no phases array");
            return;
        }
        Set<Object> ids = new HashSet<>();
        for (Object p : phases) {
            Map<String, Object> phase = Json.asObject(p);
            if (phase != null) ids.add(phase.get("id"));
        }
        for (Object p : phases) {
            Map<String, Object> phase = Json.asObject(p);
            if (phase == null || Json.asString(phase.get("id")) == null) {
                problems.add("a phase has no string id");
                continue;
            }
            Object status = phase.get("status");
            if (!(status instanceof String s && PHASE_STATUSES.contains(s))) { // Set.of(...).contains(null) throws
                problems.add("phase " + phase.get("id") + " has status " + status);
            }
            List<Object> after = Json.asArray(phase.get("after"));
            if (after != null) {
                for (Object a : after) {
                    if (!ids.contains(a)) problems.add("phase " + phase.get("id") + " comes after unknown phase " + a);
                }
            }
        }
    }
}
