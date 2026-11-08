import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * {@code catch-up} (CCW-021): the coordinator session's picture of the backlog, rebuilt from git and the working trees.
 * It prints every worktree with its ticket, branch and {@code plan.json} state, every uncommitted ticket or
 * {@code plan.json} file in {@code main} and every worktree, and the advisory proposals still waiting for the
 * coordinator. IDs, branches, states and paths only, never ticket content; no state is kept between runs.
 *
 * <pre>
 * story-gate catch-up --hook             a SessionStart hook: the session's name is the hook input's session_title,
 *                                        or else the environment variable DFS_SESSION
 * story-gate catch-up --session &lt;name&gt;   the coordinator's /tickets list
 * </pre>
 *
 * It prints only when the name maps to {@code coordinator} in {@code docs/tickets/lanes.md}; in any other session, or
 * with no name, it prints nothing and exits 0, so the hook is silent in ticket sessions. In hook mode it always exits 0
 * (a failure is one line the coordinator can read); otherwise a failure exits 2.
 *
 * <pre>
 * story-gate catch-up --session &lt;name&gt; --lanes   any other session's /tickets list (CCW-022, CCW-026): the
 *                                                worktrees of every started ticket in every lane, each with its
 *                                                next command as a launch line
 * </pre>
 *
 * Every name except the coordinator's gets that view; the coordinator or no name gets nothing.
 */
final class CatchUp {

    static final String ROLE = "coordinator";

    private static final Pattern PROPOSAL = Pattern.compile("^ADV-(\\d{3})-r(\\d+)\\.md$");
    private static final Pattern RESPONSE = Pattern.compile("^ADV-(\\d{3})-r(\\d+)\\.json$");
    private static final Pattern TICKET_BRANCH = Pattern.compile("^tickets/([A-Z]+-\\d{3})-.+$");
    private static final Pattern LANE_LINE = Pattern.compile("^([^\\s:]+):\\s*(.+)$");

    /** One entry of {@code git worktree list --porcelain}; {@code branch} is null when detached. */
    record Worktree(String path, String branch) {}

    /** One uncommitted file: the porcelain status code ({@code M}, {@code A}, {@code D}, {@code ??}, ...) and path. */
    record Dirty(String status, String path) {}

    private CatchUp() {}

    // ---- entry point

    /** The text to print (possibly empty) and the exit code. */
    record Outcome(String text, int exit) {}

    static Outcome run(Path root, String sessionArg, boolean hook, String hookInput, String envName) {
        String name;
        try {
            name = sessionArg != null ? sessionArg : sessionName(hook ? hookInput : null, envName);
        } catch (RuntimeException e) {
            return new Outcome("", 0); // an unreadable hook input names no session
        }
        if (name == null) return new Outcome("", 0);
        try {
            Path main = mainTree(root);
            String lanes = readOrNull(main.resolve("docs/tickets/lanes.md"));
            if (lanes == null || !ROLE.equals(roleOf(lanes, name))) return new Outcome("", 0);
            return new Outcome(render(main, name), 0);
        } catch (RuntimeException e) {
            return new Outcome("catch-up could not run: " + e.getMessage() + "\n", hook ? 0 : 2);
        }
    }

    /**
     * The view of every session that is not the coordinator (CCW-022, CCW-026): all started tickets of every lane,
     * each with its worktree, {@code plan.json} state and next command as a launch line. Empty text for the
     * coordinator or no name; any other name, listed in {@code lanes.md} or not, gets the view.
     */
    static Outcome runLanes(Path root, String sessionArg, String envName) {
        String name = sessionArg != null ? sessionArg : sessionName(null, envName);
        if (name == null) return new Outcome("", 0);
        try {
            Path main = mainTree(root);
            String lanesText = readOrNull(main.resolve("docs/tickets/lanes.md"));
            if (lanesText != null && ROLE.equals(roleOf(lanesText, name))) return new Outcome("", 0);
            List<String> lines = new ArrayList<>();
            for (Worktree t : worktrees(Git.run(main, "worktree", "list", "--porcelain", "-z"))) {
                Path path = Path.of(t.path());
                String id = ticketOf(t.branch());
                if (id == null || samePath(path, main)) continue;
                String ticket = ticketTextOrNull(path, id);
                if (ticket == null) continue;
                lines.add(laneLine(id, relative(main, path), planTextOrNull(path, id), frontValue(ticket, "archetype"),
                        frontValue(ticket, "status"), learningsNonEmpty(path, id)));
            }
            StringBuilder out = new StringBuilder("Catch-up for " + name + " (started tickets, every lane):\n");
            section(out, "started tickets", lines, "none");
            return new Outcome(out.toString(), 0);
        } catch (RuntimeException e) {
            return new Outcome("catch-up could not run: " + e.getMessage() + "\n", 2);
        }
    }

    /** {@code claude -n CCW-026 --model sonnet --permission-mode auto "/story run CCW-026"}: the line a new terminal starts a ticket's session from; no {@code --model} when the archetype is {@code none}. */
    static String launchLine(String id, String archetype, String command) {
        boolean pipeline = archetype != null && !archetype.isBlank() && !archetype.equals("none");
        return "claude -n " + id + (pipeline ? " --model sonnet" : "") + " --permission-mode auto \"" + command + "\"";
    }

    /** {@code CCW-022 ../repo-CCW-022-x: plan awaiting-approval at the gate after design; next: claude -n CCW-022 ...}. */
    static String laneLine(String id, String path, String planText, String archetype, String status,
            boolean learnings) {
        String state = planText == null ? "no plan.json" : planState(planText);
        String next = nextCommand(id, planText, archetype, status, learnings);
        return id + " " + path + ": " + state + "; next: " + (next.startsWith("/") ? launchLine(id, archetype, next) : next);
    }

    /**
     * The command that resumes a started ticket: {@code /story approve} at a gate, {@code /story run} when blocked
     * or running, {@code /story learn} or {@code /tickets done} when the plan is done, and for a ticket with no
     * plan.json {@code /tickets start --worktree} when its archetype is {@code none}, else {@code /story run}.
     */
    static String nextCommand(String id, String planText, String archetype, boolean learnings) {
        return nextCommand(id, planText, archetype, null, learnings);
    }

    /** As above; a hand-implemented ticket already {@code in-progress} or later is continued, not started again. */
    static String nextCommand(String id, String planText, String archetype, String status, boolean learnings) {
        if (planText == null) {
            if (!"none".equals(archetype)) return "/story run " + id;
            return status == null || status.equals("ready")
                    ? "/tickets start " + id + " --worktree"
                    : status.equals("in-review") ? "/tickets done " + id
                    : "continue in its worktree, then /tickets review " + id;
        }
        Map<String, Object> plan;
        try {
            plan = Json.asObject(Json.parse(planText));
        } catch (Json.ParseException e) {
            return "fix plan.json by hand";
        }
        String state = plan == null ? null : Json.asString(plan.get("state"));
        if (state == null) return "fix plan.json by hand";
        return switch (state) {
            case "awaiting-approval" -> "/story approve " + id;
            case "done" -> learnings || !objects(plan.get("defects")).isEmpty()
                    ? "/story learn " + id : "/tickets done " + id;
            default -> "/story run " + id;
        };
    }

    /** The value of a top-level frontmatter key, or null. */
    static String frontValue(String ticketText, String key) {
        String t = ticketText.replace("\r\n", "\n");
        if (!t.startsWith("---\n")) return null;
        int end = t.indexOf("\n---", 4);
        String front = end < 0 ? t : t.substring(4, end);
        Matcher m = Pattern.compile("(?m)^" + key + ":[ \\t]*(\\S.*?)[ \\t]*$").matcher(front);
        return m.find() ? m.group(1) : null;
    }

    private static String ticketTextOrNull(Path tree, String id) {
        try {
            return readOrNull(Story.find(tree, id).folder().resolve(id + "-ticket.md"));
        } catch (GateError e) {
            return null;
        }
    }

    private static String planTextOrNull(Path tree, String id) {
        try {
            return Story.find(tree, id).planText();
        } catch (GateError e) {
            return null;
        }
    }

    private static boolean learningsNonEmpty(Path tree, String id) {
        try {
            String t = readOrNull(Story.find(tree, id).folder().resolve("learnings.md"));
            return t != null && !t.isBlank();
        } catch (GateError e) {
            return false;
        }
    }

    /** The session's name: the hook input's {@code session_title}, else {@code envName}; null when neither is set. */
    static String sessionName(String hookInput, String envName) {
        if (hookInput != null && !hookInput.isBlank()) {
            Map<String, Object> input = Json.asObject(Json.parse(hookInput.strip()));
            String title = input == null ? null : Json.asString(input.get("session_title"));
            if (title != null && !title.isBlank()) return title.strip();
        }
        return envName == null || envName.isBlank() ? null : envName.strip();
    }

    /**
     * What {@code lanes.md} maps {@code name} to: the text after the colon on its line in the fenced block, with
     * spaces after commas normalized ({@code "backend, frontend"}, {@code "coordinator"}); null when not listed.
     */
    static String roleOf(String lanesText, String name) {
        boolean fenced = false;
        for (String raw : lanesText.replace("\r\n", "\n").split("\n")) {
            String line = raw.strip();
            if (line.startsWith("```")) {
                fenced = !fenced;
                continue;
            }
            if (!fenced) continue;
            Matcher m = LANE_LINE.matcher(line);
            if (m.matches() && m.group(1).equals(name)) {
                return String.join(", ", m.group(2).strip().split("\\s*,\\s*"));
            }
        }
        return null;
    }

    // ---- the picture

    private static String render(Path main, String name) {
        List<Worktree> trees = worktrees(Git.run(main, "worktree", "list", "--porcelain", "-z"));
        String head = Git.run(main, "rev-parse", "--short", "HEAD").strip();
        StringBuilder out = new StringBuilder();
        out.append("Catch-up for ").append(name).append(" (coordinator), main at ").append(head)
                .append(" (CCW-021; run /tickets list for the backlog):\n");

        List<String> treeLines = new ArrayList<>();
        List<String> dirtyLines = new ArrayList<>();
        dirtyLines.addAll(dirtyLines("main checkout", dirty(Git.run(main, "status", "--porcelain", "-z",
                "--untracked-files=all", "--", "docs/tickets"))));
        for (Worktree t : trees) {
            Path path = Path.of(t.path());
            if (samePath(path, main)) continue;
            String id = ticketOf(t.branch());
            treeLines.add(worktreeLine(id, t.branch(), relative(main, path), id == null ? null : planOf(path, id)));
            if (Files.isDirectory(path)) {
                dirtyLines.addAll(dirtyLines((id != null ? id : relative(main, path)) + " worktree", dirty(Git.run(path, "status",
                        "--porcelain", "-z", "--untracked-files=all", "--", "docs/tickets"))));
            }
        }
        section(out, "worktrees", treeLines, "none");
        section(out, "uncommitted ticket and plan.json files", dirtyLines, "none");
        List<String> advice = advice(read(main.resolve("advisory/proposals")), read(main.resolve("advisory/responses")));
        if (!advice.isEmpty()) section(out, "pending advice (advisory/README.md)", advice, null);
        return out.toString();
    }

    private static void section(StringBuilder out, String title, List<String> lines, String none) {
        if (lines.isEmpty()) {
            out.append(title).append(": ").append(none).append('\n');
            return;
        }
        out.append(title).append(":\n");
        for (String l : lines) out.append("- ").append(l).append('\n');
    }

    /** {@code CCW-021 tickets/CCW-021-x ../repo-CCW-021-x: plan awaiting-approval at the gate after design}. */
    static String worktreeLine(String id, String branch, String path, String plan) {
        String who = id != null ? id + " " + branch : (branch != null ? "(no ticket) " + branch : "(detached)");
        return who + " " + path + (id == null ? "" : ": " + (plan == null ? "no plan.json" : plan));
    }

    /** The ticket ID of a {@code tickets/<ID>-<slug>} branch, or null. */
    static String ticketOf(String branch) {
        if (branch == null) return null;
        Matcher m = TICKET_BRANCH.matcher(branch);
        return m.matches() ? m.group(1) : null;
    }

    private static String planOf(Path tree, String id) {
        try {
            String text = Story.find(tree, id).planText();
            return text == null ? null : planState(text);
        } catch (GateError e) {
            return null; // no ticket folder in that tree: no plan either
        }
    }

    /**
     * A plan's state in a few words: {@code plan running, phase tests}, {@code plan awaiting-approval at the gate
     * after design}, {@code plan blocked in phase implement}, {@code plan done}, or why it cannot be read.
     */
    static String planState(String planText) {
        Map<String, Object> plan;
        try {
            plan = Json.asObject(Json.parse(planText));
        } catch (Json.ParseException e) {
            return "plan.json is not valid JSON";
        }
        String state = plan == null ? null : Json.asString(plan.get("state"));
        if (state == null) return "plan.json has no state";
        List<Map<String, Object>> phases = objects(plan.get("phases"));
        String open = null;
        for (Map<String, Object> p : phases) {
            if (!"done".equals(p.get("status"))) {
                open = Json.asString(p.get("id"));
                break;
            }
        }
        return switch (state) {
            case "awaiting-approval" -> "plan awaiting-approval at the gate after " + pendingGate(plan, phases);
            case "blocked" -> "plan blocked in phase " + (open == null ? "?" : open);
            case "running" -> "plan running" + (open == null ? "" : ", phase " + open);
            default -> "plan " + state;
        };
    }

    /** The first pending gate whose phase is done ({@code done} once every phase is), else the first pending one. */
    private static String pendingGate(Map<String, Object> plan, List<Map<String, Object>> phases) {
        Map<String, Boolean> done = new TreeMap<>();
        boolean all = !phases.isEmpty();
        for (Map<String, Object> p : phases) {
            boolean d = "done".equals(p.get("status"));
            done.put(String.valueOf(p.get("id")), d);
            all &= d;
        }
        String first = null;
        for (Map<String, Object> g : objects(plan.get("gates"))) {
            if (!"pending".equals(g.get("status"))) continue;
            String after = String.valueOf(g.get("after"));
            if (first == null) first = after;
            if ("done".equals(after) ? all : done.getOrDefault(after, false)) return after;
        }
        return first == null ? "?" : first;
    }

    private static List<Map<String, Object>> objects(Object array) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Object> list = Json.asArray(array);
        if (list == null) return out;
        for (Object o : list) {
            Map<String, Object> m = Json.asObject(o);
            if (m != null) out.add(m);
        }
        return out;
    }

    // ---- git output

    /** Parses {@code git worktree list --porcelain -z}: fields end in NUL, an empty field ends an entry. */
    static List<Worktree> worktrees(String porcelainZ) {
        List<Worktree> trees = new ArrayList<>();
        String path = null, branch = null;
        for (String field : (porcelainZ + "\0").split("\0", -1)) {
            if (field.isEmpty()) {
                if (path != null) trees.add(new Worktree(path, branch));
                path = null;
                branch = null;
            } else if (field.startsWith("worktree ")) {
                path = field.substring("worktree ".length());
            } else if (field.startsWith("branch ")) {
                branch = field.substring("branch ".length()).replaceFirst("^refs/heads/", "");
            }
        }
        return trees;
    }

    /** The ticket and {@code plan.json} files in {@code git status --porcelain -z} output (renames give the new path). */
    static List<Dirty> dirty(String porcelainZ) {
        List<Dirty> files = new ArrayList<>();
        String[] fields = porcelainZ.split("\0");
        for (int i = 0; i < fields.length; i++) {
            String f = fields[i];
            if (f.length() < 4) continue;
            String code = f.substring(0, 2), path = f.substring(3);
            if (code.charAt(0) == 'R' || code.charAt(0) == 'C') i++; // the next field is the old path
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (fileName.equals("plan.json") || fileName.matches("[A-Z]+-\\d{3}-ticket\\.md")) {
                files.add(new Dirty(code.strip(), path));
            }
        }
        return files;
    }

    private static List<String> dirtyLines(String where, List<Dirty> files) {
        return files.stream().map(d -> where + ": " + d.path() + " (" + d.status() + ")").toList();
    }

    // ---- advice

    /**
     * One line per proposal waiting for the coordinator: no response, or a {@code received} one. Only files named
     * {@code ADV-NNN-rN.md} are proposals; an older revision with a newer one is flagged superseded, cross-checked
     * against the newer file's {@code Supersedes:} line; a response that is not valid JSON is reported by path.
     * Maps are file name to content.
     */
    static List<String> advice(Map<String, String> proposals, Map<String, String> responses) {
        List<String> lines = new ArrayList<>();
        Map<String, String> status = new TreeMap<>(); // "ADV-001-r1" -> status
        for (var e : responses.entrySet()) {
            Matcher m = RESPONSE.matcher(e.getKey());
            if (!m.matches()) continue;
            String key = "ADV-" + m.group(1) + "-r" + Integer.parseInt(m.group(2));
            try {
                Map<String, Object> r = Json.asObject(Json.parse(e.getValue()));
                String s = r == null ? null : Json.asString(r.get("status"));
                if (s == null) {
                    lines.add("invalid response advisory/responses/" + e.getKey() + ": no status");
                } else {
                    status.put(key, s);
                }
            } catch (Json.ParseException ex) {
                lines.add("invalid response advisory/responses/" + e.getKey() + ": " + ex.getMessage());
            }
        }
        Map<String, Integer> newest = new TreeMap<>();
        Map<String, Map<Integer, String>> byId = new TreeMap<>();
        for (var e : proposals.entrySet()) {
            Matcher m = PROPOSAL.matcher(e.getKey());
            if (!m.matches()) continue;
            String id = "ADV-" + m.group(1);
            int rev = Integer.parseInt(m.group(2));
            byId.computeIfAbsent(id, k -> new TreeMap<>()).put(rev, e.getKey());
            newest.merge(id, rev, Math::max);
        }
        byId.forEach((id, revs) -> revs.forEach((rev, file) -> {
            String s = status.get(id + "-r" + rev);
            if (s != null && !s.equals("received")) return;
            String path = "advisory/proposals/" + file;
            String text = proposals.get(file);
            int top = newest.get(id);
            if (rev < top) {
                String newer = proposals.get(revs.get(top));
                String check = supersedes(newer).contains(id + "-r" + rev) ? ""
                        : "; r" + top + "'s Supersedes line does not name r" + rev;
                lines.add(id + " r" + rev + " superseded by r" + top + " " + path
                        + " (record a superseded response" + check + ")");
            } else {
                lines.add(id + " r" + rev + " \"" + title(text, id) + "\" " + path);
            }
        }));
        return lines;
    }

    private static String title(String text, String id) {
        for (String raw : strip(text).split("\n")) {
            String line = raw.strip();
            if (line.startsWith("# ")) return line.substring(2).strip().replaceFirst("^" + id + ":\\s*", "");
        }
        return "(no title)";
    }

    private static String supersedes(String text) {
        for (String raw : strip(text).split("\n")) {
            String line = raw.strip().replaceFirst("^[-*]\\s*", "");
            if (line.startsWith("Supersedes:")) return line;
        }
        return "";
    }

    private static String strip(String text) {
        String t = text.replace("\r\n", "\n");
        return !t.isEmpty() && t.charAt(0) == 0xFEFF ? t.substring(1) : t;
    }

    // ---- files and paths

    /** The tree that has {@code main} checked out, or {@code root} when none does. */
    static Path mainTree(Path root) {
        for (Worktree t : worktrees(Git.run(root, "worktree", "list", "--porcelain", "-z"))) {
            if ("main".equals(t.branch())) return Path.of(t.path());
        }
        return root;
    }

    private static Map<String, String> read(Path dir) {
        Map<String, String> files = new TreeMap<>();
        if (!Files.isDirectory(dir)) return files;
        try (Stream<Path> list = Files.list(dir)) {
            for (Path p : list.filter(Files::isRegularFile).toList()) {
                files.put(p.getFileName().toString(), Files.readString(p));
            }
        } catch (IOException e) {
            throw new GateError("cannot read " + dir + ": " + e.getMessage(), e);
        }
        return files;
    }

    private static String readOrNull(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean samePath(Path a, Path b) {
        try {
            return Files.isSameFile(a, b);
        } catch (IOException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    private static String relative(Path main, Path tree) {
        try {
            return main.toAbsolutePath().normalize().relativize(tree.toAbsolutePath().normalize()).toString()
                    .replace('\\', '/');
        } catch (IllegalArgumentException e) { // another drive
            return tree.toString().replace('\\', '/');
        }
    }
}
