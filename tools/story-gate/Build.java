import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a Gradle run left besides test reports: its exit code, and the problems that are not a failing test (a task
 * that failed, compile errors). Gradle's own output is captured here and never shown to an agent.
 */
record Build(int exit, List<String> problems, String wentWrong) {

    private static final Pattern FAILED_TASK = Pattern.compile("(?m)^> Task (\\S+) FAILED\\s*$");
    // Not indented: Gradle repeats the same errors, indented, under "What went wrong".
    private static final Pattern COMPILE_ERROR = Pattern.compile("(?m)^(\\S.*?\\.java):(\\d+): error: (.*)$");
    private static final Pattern WENT_WRONG = Pattern.compile("(?m)^\\* What went wrong:\\s*\\R(.+)$");
    private static final int MAX_COMPILE_ERRORS = 5;

    static Build ok() {
        return new Build(0, List.of(), "");
    }

    /** Runs {@code ./gradlew <args>} from the repository root and summarises the outcome. */
    static Build gradle(Path root, String... args) {
        List<String> command = new ArrayList<>();
        command.add(root.resolve(Proc.isWindows() ? "gradlew.bat" : "gradlew").toString());
        command.addAll(List.of(args));
        command.add("--console=plain");
        Proc.Result result = Proc.run(root, command);
        return summarize(result.exit(), result.output(), root);
    }

    static Build summarize(int exit, String output, Path root) {
        List<String> problems = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Matcher e = COMPILE_ERROR.matcher(output);
        while (e.find()) errors.add(relative(e.group(1), root) + ":" + e.group(2) + ": " + e.group(3).strip());

        Matcher t = FAILED_TASK.matcher(output);
        while (t.find()) {
            String task = t.group(1);
            boolean testTask = task.equals("test") || task.endsWith(":test");
            boolean explainedByErrors = task.contains("compile") && !errors.isEmpty();
            if (!testTask && !explainedByErrors) problems.add("task " + task + " failed");
        }
        for (int i = 0; i < Math.min(errors.size(), MAX_COMPILE_ERRORS); i++) {
            problems.add("compile error " + errors.get(i));
        }
        if (errors.size() > MAX_COMPILE_ERRORS) {
            problems.add("... and " + (errors.size() - MAX_COMPILE_ERRORS) + " more compile errors");
        }
        Matcher w = WENT_WRONG.matcher(output);
        return new Build(exit, problems, w.find() ? w.group(1).strip() : "");
    }

    private static String relative(String path, Path root) {
        String p = path.replace('\\', '/');
        String prefix = root.toString().replace('\\', '/') + "/";
        return p.startsWith(prefix) ? p.substring(prefix.length()) : p;
    }
}
