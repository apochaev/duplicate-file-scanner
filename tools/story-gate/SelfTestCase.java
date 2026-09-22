import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Runs one self-test case and returns the text to compare with its {@code expected.txt}. {@code case.json} holds:
 *
 * <ul>
 * <li>{@code check}: red, green, disabled, freeze, touches, traceability, stubs, consistency, test-phase, launcher
 *     or cli (the whole program as a child process, on a console pretending to be a legacy code page);</li>
 * <li>{@code ticket} (default DFS-001) and {@code scenarios}: titles of this ticket's scenarios the case covers;</li>
 * <li>test results, inline as {@code results} ({@code class}, {@code name}, {@code outcome}, {@code type},
 *     {@code message}, {@code times} for repeats with {@code {i}} in the name) and/or a folder of real
 *     {@code reports};</li>
 * <li>{@code build}: {@code exit}, {@code output} (a file of captured Gradle output) and {@code root};</li>
 * <li>{@code files}: path to text, the source tree for disabled and stubs;</li>
 * <li>{@code touches}, {@code rootFiles}, {@code changed}, or a {@code repo}: steps that build a temporary git
 *     repository, each with {@code write} (path to text, or {@code "@file"} to copy a case file), {@code delete}
 *     and an optional {@code commit} label, which {@code base}, {@code plan} and {@code args} may refer to.</li>
 * </ul>
 *
 * Files named in a case are looked up in its own folder first, then in {@code selftest/shared}.
 */
final class SelfTestCase {

    private final Path dir, shared, root;
    private final Map<String, Object> spec;
    private Path tmp;
    private final Map<String, String> labels = new HashMap<>();

    SelfTestCase(Path dir, Path shared, Path root, Map<String, Object> spec) {
        this.dir = dir;
        this.shared = shared;
        this.root = root;
        this.spec = spec;
    }

    String execute() {
        try {
            return normalize(dispatch());
        } finally {
            if (tmp != null) deleteTree(tmp);
        }
    }

    /** Commit hashes differ on every run; the case's repo labels stand in for them. */
    private String normalize(String output) {
        String out = output;
        for (var e : labels.entrySet()) {
            out = out.replace(e.getValue(), "<" + e.getKey() + ">").replace(e.getValue().substring(0, 8),
                    "<" + e.getKey() + ">");
        }
        return out;
    }

    private String dispatch() {
        String id = str(spec, "ticket", "DFS-001");
        return switch (str(spec, "check", "")) {
            case "red" -> RedCheck.evaluate(id, results(), build()).render();
            case "green" -> GreenCheck.evaluate(id, results(), build()).render();
            case "disabled" -> DisabledCheck.evaluate(id, files(), results()).render();
            case "stubs" -> StubsCheck.evaluate(id, files()).render();
            case "touches" -> touches(id);
            case "freeze" -> freeze(id);
            case "traceability" -> TraceabilityCheck.evaluate(Ticket.parse(id, text("ticket.md")), results(),
                    testPlan()).render();
            case "consistency" -> ConsistencyCheck.evaluate(id, str(spec, "status", ""),
                    optionalText("plan.json")).render();
            case "test-phase" -> TestPhaseGate.evaluate(id, Ticket.parse(id, text("ticket.md")), results(),
                    build(), testPlan()).render();
            case "red-run" -> redRun(id);
            case "stubs-run" -> stubsRun(id);
            case "selftest" -> nestedSelfTest();
            case "launcher" -> launcher();
            case "cli" -> cli();
            default -> throw new GateError("unknown check '" + str(spec, "check", "") + "'");
        };
    }

    // ---- inputs

    private List<TestResult> results() {
        List<TestResult> all = new ArrayList<>();
        for (Object o : list(spec, "results")) {
            Map<String, Object> r = Json.asObject(o);
            int times = r.get("times") instanceof Long n ? n.intValue() : 1;
            for (int i = 1; i <= times; i++) {
                String cls = str(r, "class", "x.SampleTest"), name = str(r, "name", "test").replace("{i}", "" + i);
                all.add(switch (str(r, "outcome", "passed")) {
                    case "failed" -> TestResult.failed(cls, name, str(r, "type", ""), str(r, "message", ""));
                    case "skipped" -> TestResult.skipped(cls, name, str(r, "type", ""));
                    default -> TestResult.passed(cls, name);
                });
            }
        }
        String reports = str(spec, "reports", null);
        if (reports != null) {
            try (Stream<Path> xmls = Files.list(locate(reports))) {
                for (Path xml : xmls.filter(p -> p.toString().endsWith(".xml")).sorted().toList()) {
                    all.addAll(Junit.parse(Files.readAllBytes(xml)));
                }
            } catch (IOException e) {
                throw new GateError("cannot read reports " + reports + ": " + e.getMessage(), e);
            }
        }
        all.sort(Comparator.comparing(TestResult::className).thenComparing(TestResult::name));
        return all;
    }

    private Build build() {
        Map<String, Object> b = Json.asObject(spec.get("build"));
        if (b == null) return Build.ok();
        String output = b.containsKey("output") ? text(str(b, "output", "")) : "";
        int exit = b.get("exit") instanceof Long n ? n.intValue() : 0;
        return Build.summarize(exit, output, Path.of(str(b, "root", "/nowhere")));
    }

    private Map<String, String> files() {
        Map<String, String> files = new TreeMap<>();
        Map<String, Object> given = Json.asObject(spec.get("files"));
        if (given != null) given.forEach((path, content) -> files.put(path, String.valueOf(content)));
        return files;
    }

    // ---- git-backed checks

    private String touches(String id) {
        List<String> touches = strings("touches");
        List<Git.Change> changes = new ArrayList<>();
        Predicate<String> exists;
        if (spec.containsKey("repo")) {
            buildRepo();
            changes = Git.changesSince(tmp, label(str(spec, "base", "base")));
            exists = entry -> Files.exists(tmp.resolve(entry));
        } else {
            for (String path : strings("changed")) changes.add(new Git.Change("modified", path));
            exists = new HashSet<>(strings("rootFiles"))::contains;
        }
        return TouchesCheck.evaluate(id, touches, exists, changes).render();
    }

    private String freeze(String id) {
        buildRepo();
        String plan = optionalText("plan.json");
        if (plan != null) plan = substitute(plan);
        return FreezeCheck.run(tmp, id, plan).render();
    }

    /**
     * The real red runner against a fake project whose {@code gradlew} only produces fresh reports when it is asked
     * to {@code cleanTest}: a stale report left behind would otherwise be read as the result of the run.
     */
    private String redRun(String id) {
        copyProject();
        return RedCheck.run(tmp, id).render();
    }

    /** The real stubs runner, scanning files on disk (folder names, odd encodings) as the map-based case cannot. */
    private String stubsRun(String id) {
        copyProject();
        return StubsCheck.run(tmp, id).render();
    }

    /** SelfTest itself, on a small tree of cases and a ticket: proves that it notices a changed verdict. */
    private String nestedSelfTest() {
        copyProject();
        Verdict v = SelfTest.run(tmp, false);
        return v.render() + "exit=" + v.exitCode() + "\n";
    }

    /** Copies the case's {@code tree} folder into a fresh temporary root. */
    private void copyProject() {
        try {
            tmp = Files.createTempDirectory("story-gate-selftest-");
            Path from = locate(str(spec, "tree", "tree"));
            try (Stream<Path> files = Files.walk(from)) {
                for (Path source : files.filter(Files::isRegularFile).toList()) {
                    Path target = tmp.resolve(from.relativize(source).toString());
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                    if (target.getFileName().toString().equals("gradlew")) target.toFile().setExecutable(true);
                }
            }
            // Reports "left over from an earlier run". They cannot live under a build/ folder in this repo (gitignored).
            String stale = str(spec, "staleReports", null);
            if (stale != null) {
                Path reports = Files.createDirectories(tmp.resolve("mod").resolve("build").resolve("test-results")
                        .resolve("test"));
                try (Stream<Path> xmls = Files.list(locate(stale))) {
                    for (Path xml : xmls.toList()) Files.copy(xml, reports.resolve(xml.getFileName().toString()));
                }
            }
        } catch (IOException e) {
            throw new GateError("cannot copy the case's tree: " + e.getMessage(), e);
        }
    }

    private String cli() {
        buildRepo();
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Dfile.encoding=windows-1252",
                "-Dstdout.encoding=windows-1252", "-Dsun.stdout.encoding=windows-1252",
                root.resolve("tools").resolve("story-gate").resolve("StoryGate.java").toString()));
        for (String arg : strings("args")) command.add(substitute(arg));
        command.addAll(List.of("--root", tmp.toString()));
        Proc.Result r = Proc.run(root, command);
        return r.output().replace("\r\n", "\n") + "exit=" + r.exit() + "\n";
    }

    private void buildRepo() {
        try {
            tmp = Files.createTempDirectory("story-gate-selftest-");
            git("-c", "init.defaultBranch=main", "init", "-q");
            Map<String, Object> config = Json.asObject(spec.get("gitConfig"));
            if (config != null) config.forEach((key, value) -> git("config", key, String.valueOf(value)));
            for (Object o : list(spec, "repo")) {
                Map<String, Object> step = Json.asObject(o);
                Map<String, Object> writes = Json.asObject(step.get("write"));
                if (writes != null) {
                    for (var e : writes.entrySet()) {
                        String content = String.valueOf(e.getValue());
                        if (content.startsWith("@")) content = text(content.substring(1));
                        Path file = tmp.resolve(e.getKey());
                        Files.createDirectories(file.getParent());
                        Files.writeString(file, content);
                    }
                }
                for (Object path : list(step, "delete")) Files.delete(tmp.resolve(String.valueOf(path)));
                String label = str(step, "commit", null);
                if (label != null) {
                    git("add", "-A");
                    git("commit", "-q", "--no-verify", "-m", label);
                    labels.put(label, git("rev-parse", "HEAD").strip());
                }
            }
        } catch (IOException e) {
            throw new GateError("cannot build the temporary repository: " + e.getMessage(), e);
        }
    }

    /** Git for building the case's repository, independent of the user's own git configuration. */
    private String git(String... args) {
        return Git.runWith(tmp, List.of("user.name=selftest", "user.email=selftest@example.invalid",
                "commit.gpgsign=false"), args);
    }

    private String label(String name) {
        String hash = labels.get(name);
        if (hash == null) throw new GateError("no commit labelled '" + name + "' in the case's repo");
        return hash;
    }

    private String substitute(String text) {
        String out = text;
        for (var e : labels.entrySet()) out = out.replace("${" + e.getKey() + "}", e.getValue());
        return out;
    }

    // ---- launcher

    /** Runs each launcher this platform can (the .cmd and, where there is a sh, the shell one) against a fake JDK. */
    private String launcher() {
        try {
            tmp = Files.createTempDirectory("story-gate-selftest-");
            Path bin = Files.createDirectories(tmp.resolve("fakejdk").resolve("bin"));
            String version = str(spec, "fakeVersion", "17.0.5");
            Path sh = bin.resolve("java");
            Files.writeString(sh, "#!/bin/sh\nif [ \"$1\" = \"-version\" ]; then\n  echo 'openjdk version \"" + version
                    + "\" 2026-01-01' >&2\n  exit 0\nfi\necho \"fake-java ran: $*\"\n");
            sh.toFile().setExecutable(true);
            Files.writeString(bin.resolve("java.cmd"), "@echo off\r\nif \"%1\"==\"-version\" (\r\n"
                    + "  echo openjdk version \"" + version + "\" 2026-01-01 1>&2\r\n  exit /b 0\r\n)\r\n"
                    + "echo fake-java ran: %*\r\n");

            Map<String, String> env = new HashMap<>();
            if (bool("javaHome", true)) {
                env.put("JAVA_HOME", tmp.resolve("fakejdk").toString());
            } else {
                env.put("JAVA_HOME", null);
                env.put("PATH", bin + File.pathSeparator + System.getenv("PATH"));
            }
            Path launchers = root.resolve("tools").resolve("story-gate");
            Map<String, String> outputs = new TreeMap<>();
            if (Proc.isWindows()) {
                outputs.put("story-gate.cmd", launch(env, "cmd", "/c", launchers.resolve("story-gate.cmd").toString()));
            }
            if (shAvailable()) {
                outputs.put("story-gate", launch(env, "sh", launchers.resolve("story-gate").toString()));
            }
            if (outputs.isEmpty()) throw new GateError("no launcher can run on this platform");
            String first = outputs.values().iterator().next();
            StringBuilder result = new StringBuilder(first);
            outputs.forEach((name, out) -> {
                if (!out.equals(first)) result.append("MISMATCH: ").append(name).append(" printed differently\n");
            });
            return result.toString();
        } catch (IOException e) {
            throw new GateError("cannot build the fake JDK: " + e.getMessage(), e);
        }
    }

    private String launch(Map<String, String> env, String... command) {
        List<String> full = new ArrayList<>(List.of(command));
        full.addAll(List.of("stubs", "DFS-010"));
        Proc.Result r = Proc.run(root, full, env, true);
        // The path to StoryGate.java differs by machine and by launcher (the .cmd one quotes it): keep the name only.
        String out = r.output().replace("\r\n", "\n").replaceAll("\"?[^\\s\"]*[\\\\/]StoryGate\\.java\"?",
                "StoryGate.java");
        return out + "exit=" + r.exit() + "\n";
    }

    private boolean shAvailable() {
        try {
            return Proc.run(root, "sh", "-c", "exit 0").exit() == 0;
        } catch (GateError e) {
            return false;
        }
    }

    // ---- file lookup and small helpers

    private Path locate(String name) {
        Path own = dir.resolve(name);
        return Files.exists(own) ? own : shared.resolve(name);
    }

    private String text(String name) {
        String t = optionalText(name);
        if (t == null) throw new GateError("case file " + name + " not found");
        return t;
    }

    private String optionalText(String name) {
        Path file = locate(name);
        try {
            return Files.isRegularFile(file) ? Files.readString(file).replace("\r\n", "\n") : null;
        } catch (IOException e) {
            throw new GateError("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    /** The case's test-plan.md (or the shared one), or none at all when the case says {@code noTestPlan}. */
    private String testPlan() {
        return bool("noTestPlan", false) ? null : optionalText("test-plan.md");
    }

    private List<String> strings(String key) {
        return list(spec, key).stream().map(String::valueOf).toList();
    }

    private boolean bool(String key, boolean fallback) {
        return spec.get(key) instanceof Boolean b ? b : fallback;
    }

    static List<Object> list(Map<String, Object> map, String key) {
        List<Object> l = Json.asArray(map.get(key));
        return l == null ? List.of() : l;
    }

    static String str(Map<String, Object> map, String key, String fallback) {
        return map.get(key) instanceof String s ? s : fallback;
    }

    /** Deletes a folder tree; git leaves read-only object files that Windows will not delete otherwise. */
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
