import java.util.ArrayList;
import java.util.List;

/**
 * What a check reports: about ten lines and an exit code, so an agent never reads raw build output.
 * PASS is exit 0, FAIL (the work is wrong) is 1, ERROR (the check itself could not run) is 2.
 */
record Verdict(Status status, String check, String subject, String summary, List<String> problems) {

    enum Status { PASS, FAIL, ERROR }

    static final int MAX_LINES = 10;
    static final int MAX_WIDTH = 160;

    static Verdict pass(String check, String subject, String summary) {
        return new Verdict(Status.PASS, check, subject, summary, List.of());
    }

    /** A passing verdict with detail lines, rendered beneath the summary like a failure's problems (CCW-016). */
    static Verdict pass(String check, String subject, String summary, List<String> details) {
        return new Verdict(Status.PASS, check, subject, summary, List.copyOf(details));
    }

    /** A failing verdict; the problems are one line each, and the rendering caps how many are shown. */
    static Verdict fail(String check, String subject, List<String> problems) {
        String summary = problems.size() + (problems.size() == 1 ? " problem" : " problems");
        return new Verdict(Status.FAIL, check, subject, summary, List.copyOf(problems));
    }

    /** The first line of {@code message} is the summary; any further lines are shown beneath it. */
    static Verdict error(String check, String subject, String message) {
        String[] lines = message.split("\n");
        return new Verdict(Status.ERROR, check, subject, lines[0], List.of(lines).subList(1, lines.length));
    }

    int exitCode() {
        return switch (status) {
            case PASS -> 0;
            case FAIL -> 1;
            case ERROR -> 2;
        };
    }

    String render() {
        List<String> lines = new ArrayList<>();
        lines.add(line(status + " " + check + (subject.isEmpty() ? "" : " " + subject) + ": " + summary));
        int room = MAX_LINES - 1;
        if (problems.size() <= room) {
            for (String p : problems) lines.add(line("- " + p));
        } else {
            int shown = room - 1;
            for (int i = 0; i < shown; i++) lines.add(line("- " + problems.get(i)));
            lines.add("... and " + (problems.size() - shown) + " more");
        }
        return String.join("\n", lines) + "\n";
    }

    /** One line, no control characters, capped at {@link #MAX_WIDTH} characters (never splitting a surrogate pair). */
    private static String line(String text) {
        String flat = text.replaceAll("[\\p{Cntrl}]+", " ").strip();
        if (flat.codePointCount(0, flat.length()) <= MAX_WIDTH) return flat;
        int end = flat.offsetByCodePoints(0, MAX_WIDTH - 3);
        return flat.substring(0, end) + "...";
    }
}
