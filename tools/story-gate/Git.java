import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thin git wrapper. Paths are always read with {@code -z} (raw, NUL-separated) and decoded as UTF-8, so git's default
 * quoting of non-ASCII names (octal escapes) never reaches a check, and only stdout is parsed: git's warnings
 * (for example about line endings) travel on stderr and must not end up inside a path or a status.
 */
final class Git {

    /** One changed path; {@code status} is added, modified, deleted or changed-type. */
    record Change(String status, String path) {}

    private Git() {}

    static String run(Path root, String... args) {
        return runWith(root, List.of(), args);
    }

    /** {@code config} entries become {@code -c} options, for callers that must not depend on the user's git setup. */
    static String runWith(Path root, List<String> config, String... args) {
        Proc.Result r = execute(root, config, args);
        if (r.exit() != 0) {
            throw new GateError("git " + String.join(" ", args) + " failed: " + firstLine(r.errors()));
        }
        return r.output();
    }

    private static Proc.Result execute(Path root, List<String> config, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-c", "core.quotepath=false"));
        for (String c : config) {
            command.add("-c");
            command.add(c);
        }
        command.addAll(List.of(args));
        return Proc.run(root, command, Map.of(), false);
    }

    static Path topLevel(Path dir) {
        return Path.of(run(dir, "rev-parse", "--show-toplevel").strip());
    }

    static String head(Path root) {
        return run(root, "rev-parse", "HEAD").strip();
    }

    /** Everything that differs between {@code base} and the working tree: committed, staged, unstaged and untracked. */
    static List<Change> changesSince(Path root, String base) {
        List<Change> changes = new ArrayList<>();
        String[] diff = run(root, "diff", "--name-status", "-z", "--no-renames", base, "--").split("\0");
        for (int i = 0; i + 1 < diff.length; i += 2) {
            changes.add(new Change(statusName(diff[i]), diff[i + 1]));
        }
        for (String path : run(root, "ls-files", "-z", "--others", "--exclude-standard").split("\0")) {
            if (!path.isEmpty()) changes.add(new Change("added", path));
        }
        return changes;
    }

    /** The commit where the current branch left the main line, so a story branch is judged by its own changes. */
    static String defaultBase(Path root) {
        for (String branch : List.of("main", "master", "origin/main", "origin/master")) {
            Proc.Result r = execute(root, List.of(), "merge-base", "HEAD", branch);
            if (r.exit() == 0 && !r.output().isBlank()) return r.output().strip();
        }
        throw new GateError("cannot find a base branch (main or master); pass --base <ref>");
    }

    private static String statusName(String code) {
        return switch (code.isEmpty() ? '?' : code.charAt(0)) {
            case 'A' -> "added";
            case 'D' -> "deleted";
            case 'T' -> "changed-type";
            default -> "modified";
        };
    }

    private static String firstLine(String s) {
        String t = s.strip();
        int nl = t.indexOf('\n');
        return nl < 0 ? t : t.substring(0, nl);
    }
}
