import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Finds the project's Java and Kotlin sources: {@code <module>/src/main/**} is production, {@code src/test/**} is test.
 * Tooling and generated folders are skipped, so the fixtures under {@code tools/} never look like production code.
 * Nothing is skipped below a {@code src} folder: a package may be called {@code tools} or {@code build}.
 */
final class Sources {

    /** Skipped wherever they occur above {@code src}. */
    private static final Set<String> SKIPPED_ANYWHERE = Set.of(".git", ".gradle", ".claude", ".idea", "build",
            "node_modules");
    /** Skipped only at the top of the repository, where a module of that name is not expected. */
    private static final Set<String> SKIPPED_AT_TOP = Set.of("docs", "gradle", "tools");

    private Sources() {}

    /** Whether a directory is left out of every scan of the repository at {@code root}. */
    static boolean skipped(Path root, Path dir) {
        Path relative = root.relativize(dir);
        int depth = relative.getNameCount();
        if (dir.equals(root) || depth == 0) return false;
        for (int i = 0; i < depth - 1; i++) {
            if (relative.getName(i).toString().equals("src")) return false;
        }
        String name = relative.getFileName().toString();
        return SKIPPED_ANYWHERE.contains(name) || (depth == 1 && SKIPPED_AT_TOP.contains(name));
    }

    static Map<String, String> production(Path root) {
        return scan(root, "src/main/");
    }

    static Map<String, String> test(Path root) {
        return scan(root, "src/test/");
    }

    static boolean isTestPath(String path) {
        return path.startsWith("src/test/") || path.contains("/src/test/");
    }

    /** The 1-based number of the first line of {@code text} that satisfies {@code test}, or 0 when none does. */
    static int firstLine(String text, Predicate<String> test) {
        String[] lines = text.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            if (test.test(lines[i])) return i + 1;
        }
        return 0;
    }

    /** Relative path (forward slashes) to file text, for every source file under a folder with the given segment. */
    private static Map<String, String> scan(Path root, String segment) {
        Map<String, String> found = new TreeMap<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return skipped(root, dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String rel = root.relativize(file).toString().replace('\\', '/');
                    boolean source = rel.endsWith(".java") || rel.endsWith(".kt");
                    if (source && (rel.startsWith(segment) || rel.contains("/" + segment))) {
                        // Lenient: one source file in another encoding must not make the whole scan unrunnable.
                        found.put(rel, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new GateError("cannot scan sources: " + e.getMessage(), e);
        }
        return found;
    }
}
