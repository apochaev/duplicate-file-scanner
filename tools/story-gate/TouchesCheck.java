import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Every changed file must be inside the ticket's {@code touches}, its own story folder, or the tests of a touched
 * package. An entry is a path if it contains a slash or exists at the repository root; otherwise a lowercase dotted
 * name is a Java package, meaning {@code <module>/src/{main,test}/java/<package as path>/**}. Anything else (such as
 * {@code module-info.java} or test resources) must be listed as an explicit path, and is named when missing.
 */
final class TouchesCheck {

    private static final Pattern PACKAGE = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");

    private TouchesCheck() {}

    static Verdict run(Path root, String id, String base) {
        Ticket ticket = Story.find(root, id).ticket();
        String from = base != null ? base : Git.defaultBase(root);
        Verdict verdict = evaluate(id, ticket.touches(), entry -> Files.exists(root.resolve(entry)),
                Git.changesSince(root, from));
        // On main itself the default base is HEAD, and commits already made there are invisible: say so, not PASS.
        if (base == null && verdict.status() == Verdict.Status.PASS && from.equals(Git.head(root))) {
            return Verdict.pass("touches", id, verdict.summary()
                    + "; base is HEAD, so only uncommitted changes were seen (pass --base <ref>)");
        }
        return verdict;
    }

    static Verdict evaluate(String id, List<String> touches, Predicate<String> existsAtRoot,
                            List<Git.Change> changes) {
        List<Pattern> allowed = new ArrayList<>();
        allowed.add(Pattern.compile("^docs/tickets/(backlog|completed)/" + Pattern.quote(id) + "-[^/]+/.+$"));
        for (String raw : touches) {
            String entry = raw.strip().replace('\\', '/');
            while (entry.startsWith("./")) entry = entry.substring(2);
            while (entry.endsWith("/")) entry = entry.substring(0, entry.length() - 1);
            if (entry.isEmpty()) continue;
            boolean isPath = entry.contains("/") || existsAtRoot.test(entry) || !PACKAGE.matcher(entry).matches();
            allowed.add(isPath
                    ? Pattern.compile("^" + Pattern.quote(entry) + "(/.*)?$")
                    : Pattern.compile("^(?:.*/)?src/(?:main|test)/java/" + Pattern.quote(entry.replace('.', '/'))
                            + "/.+$"));
        }

        List<String> problems = new ArrayList<>();
        for (Git.Change c : changes) {
            String path = c.path();
            if (allowed.stream().anyMatch(p -> p.matcher(path).matches())) continue;
            problems.add(path.endsWith("module-info.java")
                    ? path + ": outside touches; module-info.java is in no package, so list it as a path"
                    : path + ": outside touches");
        }
        if (!problems.isEmpty()) return Verdict.fail("touches", id, problems);
        return Verdict.pass("touches", id, changes.size() + " changed files, all inside touches");
    }
}
