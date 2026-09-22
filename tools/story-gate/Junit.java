import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Reads the JUnit XML reports Gradle writes to {@code <module>/build/test-results/test/TEST-*.xml}. */
final class Junit {

    private Junit() {}

    /** Every report of every module under {@code root}, in a stable order. */
    static List<TestResult> readAll(Path root) {
        List<TestResult> all = new ArrayList<>();
        for (Path report : findReports(root)) {
            try {
                all.addAll(parse(Files.readAllBytes(report)));
            } catch (IOException e) {
                throw new GateError("cannot read " + report + ": " + e.getMessage(), e);
            }
        }
        all.sort(Comparator.comparing(TestResult::className).thenComparing(TestResult::name));
        return all;
    }

    static List<Path> findReports(Path root) {
        List<Path> reports = new ArrayList<>();
        try {
            Files.walkFileTree(root, Set.of(), 5, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (dir.equals(root)) return FileVisitResult.CONTINUE;
                    if (name.equals("src")) return FileVisitResult.SKIP_SUBTREE; // reports are never inside sources
                    if (name.equals("build")) {
                        Path results = dir.resolve("test-results").resolve("test");
                        if (Files.isDirectory(results)) {
                            try (var files = Files.list(results)) {
                                files.filter(f -> f.getFileName().toString().startsWith("TEST-")
                                        && f.getFileName().toString().endsWith(".xml")).forEach(reports::add);
                            }
                        }
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return Sources.skipped(root, dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new GateError("cannot scan for test reports: " + e.getMessage(), e);
        }
        reports.sort(Comparator.naturalOrder());
        return reports;
    }

    static List<TestResult> parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml));
            List<TestResult> results = new ArrayList<>();
            NodeList cases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                results.add(testCase((Element) cases.item(i)));
            }
            return results;
        } catch (Exception e) {
            throw new GateError("malformed test report: " + e.getMessage(), e);
        }
    }

    private static TestResult testCase(Element tc) {
        String className = tc.getAttribute("classname");
        String name = tc.getAttribute("name");
        for (String tag : List.of("failure", "error")) {
            Element failure = child(tc, tag);
            if (failure != null) {
                String type = failure.getAttribute("type");
                return TestResult.failed(className, name, type, messageOf(failure, type));
            }
        }
        Element skipped = child(tc, "skipped");
        if (skipped != null) return TestResult.skipped(className, name, skipped.getAttribute("type"));
        return TestResult.passed(className, name);
    }

    /** The message attribute, or the first line of the trace when there is none, without a leading class name. */
    private static String messageOf(Element failure, String type) {
        String message = failure.hasAttribute("message")
                ? failure.getAttribute("message")
                : failure.getTextContent().lines().findFirst().orElse("");
        if (!type.isEmpty()) {
            if (message.startsWith(type + ": ")) message = message.substring(type.length() + 2);
            else if (message.equals(type)) message = "";
        }
        return message.strip();
    }

    private static Element child(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }
}
