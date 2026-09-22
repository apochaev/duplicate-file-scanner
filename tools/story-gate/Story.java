import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** A story's files: the ticket, and next to it {@code plan.json} and {@code test-plan.md}. */
record Story(String id, Path folder) {

    static Story find(Path root, String id) {
        for (String state : List.of("backlog", "completed")) {
            Path parent = root.resolve("docs").resolve("tickets").resolve(state);
            if (!Files.isDirectory(parent)) continue;
            try (Stream<Path> dirs = Files.list(parent)) {
                for (Path dir : (Iterable<Path>) dirs::iterator) {
                    if (dir.getFileName().toString().startsWith(id + "-")
                            && Files.isRegularFile(dir.resolve(id + "-ticket.md"))) {
                        return new Story(id, dir);
                    }
                }
            } catch (IOException e) {
                throw new GateError("cannot list " + parent + ": " + e.getMessage(), e);
            }
        }
        throw new GateError("no ticket folder for " + id + " under docs/tickets/{backlog,completed}");
    }

    Ticket ticket() {
        return Ticket.parse(id, read(folder.resolve(id + "-ticket.md")));
    }

    /** The plan's text, or null when the story has no plan.json yet. */
    String planText() {
        Path plan = folder.resolve("plan.json");
        return Files.isRegularFile(plan) ? read(plan) : null;
    }

    /** The test plan's text, or null when it has not been written. */
    String testPlanText() {
        Path plan = folder.resolve("test-plan.md");
        return Files.isRegularFile(plan) ? read(plan) : null;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new GateError("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    /** The commit recorded by the {@code tests} phase of a parsed plan.json, or null when there is none. */
    static String testsCommit(Object plan) {
        Map<String, Object> root = Json.asObject(plan);
        List<Object> phases = root == null ? null : Json.asArray(root.get("phases"));
        if (phases == null) return null;
        for (Object p : phases) {
            Map<String, Object> phase = Json.asObject(p);
            if (phase != null && "tests".equals(phase.get("id"))) {
                String commit = Json.asString(phase.get("commit"));
                return commit == null || commit.isBlank() ? null : commit.strip();
            }
        }
        return null;
    }
}
