import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The story pipeline's deterministic gates (DFS-010). Each check prints a verdict of about ten lines and sets the
 * exit code: 0 pass, 1 fail, 2 the check could not run. Gradle output and stack traces never reach the caller.
 *
 * <pre>
 * story-gate red|green|disabled|freeze|touches|traceability|stubs|consistency &lt;ID&gt; [--base &lt;ref&gt;] [--root &lt;dir&gt;]
 * story-gate test-phase &lt;ID&gt;   red, then traceability off the same reports: the end-of-test-author gate
 * story-gate selftest [--root &lt;dir&gt;] [--bless]
 * </pre>
 *
 * Run it through the {@code story-gate} launcher (which finds a JDK 22 or later), or directly with
 * {@code java tools/story-gate/StoryGate.java ...}. {@code --base} only matters to {@code touches}: the ref to
 * compare against, by default where the current branch left main.
 */
public class StoryGate {

    private static final List<String> CHECKS = List.of(
            "red", "green", "disabled", "freeze", "touches", "traceability", "stubs", "consistency", "test-phase");

    public static void main(String[] args) {
        // An explicit UTF-8 stream, so verdicts are exact whatever the console's code page is.
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        int exit;
        try {
            exit = run(args, out);
        } catch (GateError e) {
            out.print(Verdict.error(checkName(args), "", e.getMessage()).render());
            exit = 2;
        } catch (Throwable t) { // even an Error is "could not run" (2), never "the work failed" (1), and never a trace
            out.print(Verdict.error(checkName(args), "", "unexpected " + t.getClass().getSimpleName() + ": "
                    + t.getMessage()).render());
            exit = 2;
        }
        out.flush();
        System.exit(exit);
    }

    private static int run(String[] args, PrintStream out) {
        List<String> positional = new ArrayList<>();
        String base = null, rootArg = null;
        boolean bless = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--base" -> base = value(args, ++i, "--base");
                case "--root" -> rootArg = value(args, ++i, "--root");
                case "--bless" -> bless = true;
                default -> positional.add(args[i]);
            }
        }
        if (positional.isEmpty()) throw new GateError(usage());
        String check = positional.get(0);
        Path root = rootArg != null ? Path.of(rootArg).toAbsolutePath().normalize()
                : Git.topLevel(Path.of("").toAbsolutePath());
        if (!Files.isDirectory(root)) throw new GateError("root " + root + " is not a directory");

        Verdict verdict;
        if (check.equals("selftest")) {
            verdict = SelfTest.run(root, bless);
        } else {
            if (!CHECKS.contains(check)) throw new GateError("unknown check '" + check + "'\n" + usage());
            if (positional.size() != 2) throw new GateError(check + " needs a ticket ID\n" + usage());
            String id = positional.get(1);
            if (!id.matches("[A-Z]+-\\d+")) throw new GateError("'" + id + "' is not a ticket ID like DFS-001");
            verdict = switch (check) {
                case "red" -> RedCheck.run(root, id);
                case "green" -> GreenCheck.run(root, id);
                case "disabled" -> DisabledCheck.run(root, id);
                case "freeze" -> FreezeCheck.run(root, id);
                case "touches" -> TouchesCheck.run(root, id, base);
                case "traceability" -> TraceabilityCheck.run(root, id);
                case "stubs" -> StubsCheck.run(root, id);
                case "test-phase" -> TestPhaseGate.run(root, id);
                default -> ConsistencyCheck.run(root, id);
            };
        }
        out.print(verdict.render());
        return verdict.exitCode();
    }

    private static String value(String[] args, int i, String flag) {
        if (i >= args.length) throw new GateError(flag + " needs a value");
        return args[i];
    }

    private static String checkName(String[] args) {
        return args.length > 0 && !args[0].startsWith("--") ? args[0] : "story-gate";
    }

    private static String usage() {
        return "usage: story-gate <check> <ID> [--base <ref>] [--root <dir>]\nchecks: " + String.join(", ", CHECKS)
                + "\nor: story-gate selftest [--bless]";
    }
}
