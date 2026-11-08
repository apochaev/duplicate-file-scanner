import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The {@code catch-up} subcommand's cases, part of {@code selftest} (CCW-021.AC-15). Written in code like
 * {@link JsonSelfTest} rather than as case folders: a {@code CCW-021.AC-n-...} folder would make {@link SelfTest}'s
 * coverage rule claim every CCW-021 scenario, most of which are live runs across sessions, not catch-up output.
 * Each case is named after the criterion it proves.
 */
final class CatchUpSelfTest {

    private static final String LANES = """
            # Lanes

            ```
            dfs-product: coordinator
            ```
            """;

    private CatchUpSelfTest() {}

    static List<String> run() {
        List<String> problems = new ArrayList<>();

        // AC-12: the session's name and the lane map.
        expect(problems, "AC-12 name from the hook input's session_title", "dfs-product",
                CatchUp.sessionName("{\"session_id\":\"x\",\"source\":\"clear\",\"session_title\":\"dfs-product\"}",
                        "dfs-backend"));
        expect(problems, "AC-12 name from DFS_SESSION when the input has no title", "dfs-product",
                CatchUp.sessionName("{\"session_id\":\"x\",\"source\":\"startup\"}", "dfs-product"));
        expect(problems, "AC-12 lane map roles", "coordinator|null|backend, frontend",
                CatchUp.roleOf(LANES, "dfs-product") + "|" + CatchUp.roleOf(LANES, "CCW-099") + "|"
                        + CatchUp.roleOf("```\ndfs-x: backend,frontend\n```\n", "dfs-x"));
        CatchUp.Outcome none = CatchUp.run(Path.of("nowhere"), null, true, "{\"source\":\"clear\"}", null);
        expect(problems, "AC-15 no session name: no output, exit 0", "''/0", "'" + none.text() + "'/" + none.exit());

        // AC-9 and AC-14: worktree lines and plan states.
        List<CatchUp.Worktree> trees = CatchUp.worktrees("worktree C:/r\0HEAD 1\0branch refs/heads/main\0\0"
                + "worktree C:/r-DFS-051-x\0HEAD 2\0branch refs/heads/tickets/DFS-051-x\0\0"
                + "worktree C:/r-tmp\0HEAD 3\0detached\0\0");
        expect(problems, "AC-15 worktree list parsed", "[Worktree[path=C:/r, branch=main], "
                + "Worktree[path=C:/r-DFS-051-x, branch=tickets/DFS-051-x], Worktree[path=C:/r-tmp, branch=null]]",
                trees.toString());
        String plan = """
                {"state": "%s", "phases": [{"id": "design", "status": "done"}, {"id": "tests", "status": "%s"},
                 {"id": "implement", "status": "pending"}],
                 "gates": [{"after": "design", "status": "%s"}, {"after": "done", "status": "pending"}]}""";
        expect(problems, "AC-14 awaiting-approval names the pending gate",
                "DFS-051 tickets/DFS-051-x ../r-DFS-051-x: plan awaiting-approval at the gate after design",
                CatchUp.worktreeLine(CatchUp.ticketOf("tickets/DFS-051-x"), "tickets/DFS-051-x", "../r-DFS-051-x",
                        CatchUp.planState(plan.formatted("awaiting-approval", "pending", "pending"))));
        expect(problems, "AC-14 blocked names the blocked phase", "plan blocked in phase tests",
                CatchUp.planState(plan.formatted("blocked", "in-progress", "approved")));
        expect(problems, "AC-9 running names the phase; bad JSON is said", "plan running, phase tests|plan.json is "
                + "not valid JSON", CatchUp.planState(plan.formatted("running", "pending", "approved")) + "|"
                + CatchUp.planState("{"));

        // AC-11: advice.
        Map<String, String> proposals = new TreeMap<>(Map.of(
                "ADV-001-r1.md", "\uFEFF# ADV-001: Received one\n\n- Supersedes: None\n",
                "ADV-002-r1.md", "# ADV-002: Evaluated one\n",
                "ADV-003-r1.md", "# ADV-003: Old one\n",
                "ADV-003-r2.md", "# ADV-003: New one\n\n- Supersedes: ADV-003-r1\n",
                "ADV-004-r1.md.tmp", "# ADV-004: Half written\n",
                "notes.md", "# not a proposal\n"));
        Map<String, String> responses = new TreeMap<>(Map.of(
                "ADV-001-r1.json", "{\"status\": \"received\", \"disposition\": \"pending\"}",
                "ADV-002-r1.json", "{\"status\": \"evaluated\", \"disposition\": \"decline\"}",
                "ADV-005-r1.json", "{\"status\": "));
        expect(problems, "AC-11 pending, evaluated, superseded, .tmp and an invalid response", String.join("\n",
                        "invalid response advisory/responses/ADV-005-r1.json: unexpected end of input at line 1, column 12",
                        "ADV-001 r1 \"Received one\" advisory/proposals/ADV-001-r1.md",
                        "ADV-003 r1 superseded by r2 advisory/proposals/ADV-003-r1.md (record a superseded response)",
                        "ADV-003 r2 \"New one\" advisory/proposals/ADV-003-r2.md"),
                String.join("\n", CatchUp.advice(proposals, responses)));
        proposals.put("ADV-003-r2.md", "# ADV-003: New one\n\n- Supersedes: None\n");
        expect(problems, "AC-11 a Supersedes line that does not name the old revision is flagged",
                "ADV-003 r1 superseded by r2 advisory/proposals/ADV-003-r1.md (record a superseded response; "
                        + "r2's Supersedes line does not name r1)",
                CatchUp.advice(Map.of("ADV-003-r1.md", "# x\n", "ADV-003-r2.md", proposals.get("ADV-003-r2.md")),
                        Map.of()).get(0));

        // CCW-022.AC-8: next commands and the lane filter.
        String awaiting = plan.formatted("awaiting-approval", "done", "pending");
        expect(problems, "CCW-022.AC-8 gate: approve", "/story approve DFS-051",
                CatchUp.nextCommand("DFS-051", awaiting, "pipeline-stage", false));
        expect(problems, "CCW-022.AC-8 blocked and running: run", "/story run DFS-051|/story run DFS-051",
                CatchUp.nextCommand("DFS-051", plan.formatted("blocked", "in-progress", "approved"), "x", false) + "|"
                        + CatchUp.nextCommand("DFS-051", plan.formatted("running", "pending", "approved"), "x", false));
        expect(problems, "CCW-022.AC-8 done: learn with learnings or defects, else done",
                "/story learn DFS-051|/story learn DFS-051|/tickets done DFS-051",
                CatchUp.nextCommand("DFS-051", plan.formatted("done", "done", "approved"), "x", true) + "|"
                        + CatchUp.nextCommand("DFS-051", "{\"state\": \"done\", \"defects\": [{\"id\": 1}]}", "x", false)
                        + "|" + CatchUp.nextCommand("DFS-051", "{\"state\": \"done\"}", "x", false));
        expect(problems, "CCW-022.AC-8 no plan: start for none, run for a pipeline ticket",
                "/tickets start CCW-022 --worktree|/story run DFS-051",
                CatchUp.nextCommand("CCW-022", null, "none", false) + "|"
                        + CatchUp.nextCommand("DFS-051", null, "pipeline-stage", false));
        expect(problems, "CCW-022.AC-8 a started hand-implemented ticket is continued, not started again",
                "continue in its worktree, then /tickets review CCW-022|/tickets done CCW-022",
                CatchUp.nextCommand("CCW-022", null, "none", "in-progress", false) + "|"
                        + CatchUp.nextCommand("CCW-022", null, "none", "in-review", false));
        expect(problems, "CCW-022.AC-8 frontmatter values", "backend|none|null",
                CatchUp.frontValue("---\nid: X\nlane: backend\narchetype: none\n---\nlane: frontend\n", "lane") + "|"
                        + CatchUp.frontValue("---\nlane: a\narchetype: none  \n---\n", "archetype") + "|"
                        + CatchUp.frontValue("---\nlane: a\n---\n", "archetype"));

        problems.addAll(laneCase());
        problems.addAll(repoCase());
        return problems.stream().map(p -> "catch-up: " + p).toList();
    }

    /**
     * AC-9 and AC-15 on a real repository: main plus a ticket worktree with a plan awaiting approval and an
     * uncommitted ticket edit; a lane session prints nothing there.
     */
    private static List<String> repoCase() {
        List<String> problems = new ArrayList<>();
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("story-gate-catchup-");
            Path main = Files.createDirectories(tmp.resolve("repo"));
            git(main, "-c", "init.defaultBranch=main", "init", "-q");
            write(main.resolve("docs/tickets/lanes.md"), LANES);
            Path folder = main.resolve("docs/tickets/backlog/DFS-051-x");
            write(folder.resolve("DFS-051-ticket.md"), "---\nid: DFS-051\n---\nSecret ticket text\n");
            git(main, "add", "-A");
            git(main, "commit", "-q", "--no-verify", "-m", "base");
            git(main, "worktree", "add", "-q", "-b", "tickets/DFS-051-x", tmp.resolve("repo-DFS-051-x").toString());
            Path tree = tmp.resolve("repo-DFS-051-x");
            write(tree.resolve("docs/tickets/backlog/DFS-051-x/plan.json"), """
                    {"state": "awaiting-approval", "phases": [{"id": "design", "status": "done"}],
                     "gates": [{"after": "design", "status": "pending"}]}""");
            write(tree.resolve("docs/tickets/backlog/DFS-051-x/DFS-051-ticket.md"),
                    "---\nid: DFS-051\nstatus: in-progress\n---\nSecret ticket text\n");

            CatchUp.Outcome lane = CatchUp.run(tree, "CCW-099", false, null, null);
            expect(problems, "CCW-026.AC-7 a ticket session prints nothing from the hook view", "''/0", "'" + lane.text() + "'/" + lane.exit());

            CatchUp.Outcome out = CatchUp.run(tree, null, true, "{\"session_title\":\"dfs-product\"}", null);
            String text = out.text().replaceAll("main at [0-9a-f]+", "main at <sha>");
            expect(problems, "AC-9 worktree line and uncommitted files from a real repository", String.join("\n",
                    "Catch-up for dfs-product (coordinator), main at <sha> (CCW-021; run /tickets list for the backlog):",
                    "worktrees:",
                    "- DFS-051 tickets/DFS-051-x ../repo-DFS-051-x: plan awaiting-approval at the gate after design",
                    "uncommitted ticket and plan.json files:",
                    "- DFS-051 worktree: docs/tickets/backlog/DFS-051-x/DFS-051-ticket.md (M)",
                    "- DFS-051 worktree: docs/tickets/backlog/DFS-051-x/plan.json (??)", "") + "/0",
                    text + "/" + out.exit());
            if (text.contains("Secret")) problems.add("AC-9 the catch-up printed ticket content");
        } catch (IOException | RuntimeException e) {
            problems.add("the repository case failed to run: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (tmp != null) deleteTree(tmp);
        }
        return problems;
    }

    /**
     * CCW-026.AC-8 on a real repository: a backend ticket at a gate and a harness ticket that is blocked each have a
     * worktree; a ticket-named session sees both with a launch line, the coordinator and no name see nothing.
     */
    private static List<String> laneCase() {
        List<String> problems = new ArrayList<>();
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("story-gate-lanes-");
            Path main = Files.createDirectories(tmp.resolve("repo"));
            git(main, "-c", "init.defaultBranch=main", "init", "-q");
            write(main.resolve("docs/tickets/lanes.md"), LANES);
            write(main.resolve("docs/tickets/backlog/DFS-061-b/DFS-061-ticket.md"),
                    "---\nid: DFS-061\nlane: backend\narchetype: pipeline-stage\n---\nSecret ticket text\n");
            write(main.resolve("docs/tickets/backlog/CCW-062-h/CCW-062-ticket.md"),
                    "---\nid: CCW-062\nlane: harness\narchetype: none\n---\nSecret ticket text\n");
            git(main, "add", "-A");
            git(main, "commit", "-q", "--no-verify", "-m", "base");
            String gate = """
                    {"state": "awaiting-approval", "phases": [{"id": "design", "status": "done"}],
                     "gates": [{"after": "design", "status": "pending"}]}""";
            String blocked = """
                    {"state": "blocked", "phases": [{"id": "design", "status": "done"}, {"id": "tests", "status": "pending"}],
                     "gates": []}""";
            for (String[] t : new String[][] {{"DFS-061", "b", gate}, {"CCW-062", "h", blocked}}) {
                Path tree = tmp.resolve("repo-" + t[0] + "-" + t[1]);
                git(main, "worktree", "add", "-q", "-b", "tickets/" + t[0] + "-" + t[1], tree.toString());
                write(tree.resolve("docs/tickets/backlog/" + t[0] + "-" + t[1] + "/plan.json"), t[2]);
            }
            CatchUp.Outcome session = CatchUp.runLanes(main, "CCW-099", null);
            String text = session.text();
            expect(problems, "CCW-026.AC-8 a ticket-named session sees every lane with launch lines", true,
                    text.contains("- DFS-061 ../repo-DFS-061-b: plan awaiting-approval at the gate after design; next: "
                            + "claude -n DFS-061 --model sonnet --permission-mode auto \"/story approve DFS-061\"")
                    && text.contains("- CCW-062 ../repo-CCW-062-h: plan blocked in phase tests; next: "
                            + "claude -n CCW-062 --permission-mode auto \"/story run CCW-062\"")
                    && session.exit() == 0);
            if (text.contains("Secret")) problems.add("CCW-026.AC-8 the view shows ticket content");
            CatchUp.Outcome coordinator = CatchUp.runLanes(main, "dfs-product", null);
            CatchUp.Outcome nameless = CatchUp.runLanes(main, null, null);
            expect(problems, "CCW-026.AC-8 the coordinator and no name get nothing", "''|''",
                    "'" + coordinator.text() + "'|'" + nameless.text() + "'");
            CatchUp.Outcome hook = CatchUp.run(main, null, true, "{\"session_title\":\"dfs-product\"}", null);
            CatchUp.Outcome hookOther = CatchUp.run(main, null, true, "{\"session_title\":\"CCW-099\"}", null);
            expect(problems, "CCW-026.AC-7 the hook prints for the coordinator and nothing for a ticket name", true,
                    hook.text().startsWith("Catch-up for dfs-product (coordinator)") && hookOther.text().isEmpty());
        } catch (IOException | RuntimeException e) {
            problems.add("the lane case failed to run: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (tmp != null) deleteTree(tmp);
        }
        return problems;
    }

    private static void git(Path dir, String... args) {
        Git.runWith(dir, List.of("user.name=selftest", "user.email=selftest@example.invalid", "commit.gpgsign=false"),
                args);
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private static void expect(List<String> problems, String what, Object expected, Object actual) {
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            problems.add(what + ": expected \"" + expected + "\" but got \"" + actual + "\"");
        }
    }

    private static void deleteTree(Path tree) {
        try {
            Files.walkFileTree(tree, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    file.toFile().setWritable(true);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // a leftover temp folder is harmless
        }
    }
}
