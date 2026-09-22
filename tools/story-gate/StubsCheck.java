import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** No {@code not implemented:} marker remains in production source: every architect stub has been replaced. */
final class StubsCheck {

    private StubsCheck() {}

    static Verdict run(Path root, String id) {
        return evaluate(id, Sources.production(root));
    }

    static Verdict evaluate(String id, Map<String, String> productionSources) {
        List<String> problems = new ArrayList<>();
        productionSources.forEach((path, text) -> {
            int line = Sources.firstLine(text, l -> l.contains(TestResult.STUB_PREFIX));
            if (line > 0) problems.add(path + ":" + line + ": stub marker remains");
        });
        if (!problems.isEmpty()) return Verdict.fail("stubs", id, problems);
        return Verdict.pass("stubs", id, "no stub markers in " + productionSources.size() + " production files");
    }
}
