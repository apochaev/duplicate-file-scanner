/**
 * One test case from a JUnit XML report. For a failure, {@code failureType} is the exception class and
 * {@code failureMessage} its message without the leading class name; {@code rootType} and {@code rootMessage} are
 * the same for the failure's root cause, the last unindented {@code Caused by:} line of its trace, or empty when the
 * trace has none (CCW-014). For a skip, {@code skipType} is the exception behind it (an assumption failure), or
 * empty when the test was disabled.
 */
record TestResult(String className, String name, Outcome outcome, String failureType, String failureMessage,
                  String rootType, String rootMessage, String skipType) {

    enum Outcome { PASSED, FAILED, SKIPPED }

    static final String STUB_EXCEPTION = "java.lang.UnsupportedOperationException";
    /** A TypeScript stub's failure type in a Vitest report: {@code throw new Error('not implemented: <ID>')} (CCW-020). */
    static final String TS_STUB_ERROR = "Error";
    static final String STUB_PREFIX = "not implemented:";
    /** How a Vitest JUnit report joins the {@code describe} names and the test's own name (CCW-020). */
    private static final String VITEST_SEPARATOR = " > ";

    static TestResult passed(String className, String name) {
        return new TestResult(className, name, Outcome.PASSED, "", "", "", "", "");
    }

    static TestResult failed(String className, String name, String type, String message) {
        return failed(className, name, type, message, "", "");
    }

    static TestResult failed(String className, String name, String type, String message, String rootType,
                             String rootMessage) {
        return new TestResult(className, name, Outcome.FAILED, type, message, rootType, rootMessage, "");
    }

    static TestResult skipped(String className, String name, String skipType) {
        return new TestResult(className, name, Outcome.SKIPPED, "", "", "", "", skipType);
    }

    /**
     * A test case from a Vitest report: its {@code classname} is the spec file's path relative to the frontend
     * project ({@code src/app/app.spec.ts}), not a Java class (CCW-020).
     */
    boolean isVitest() {
        return className.endsWith(".ts");
    }

    /**
     * The class without its package, and without any outer class ({@code a.b.Outer$Inner} gives {@code Outer}). For
     * a Vitest test case, the spec file's name ({@code app.spec.ts}).
     */
    String outerClass() {
        if (isVitest()) return fileName();
        String simple = className.substring(className.lastIndexOf('.') + 1);
        int dollar = simple.indexOf('$');
        return dollar < 0 ? simple : simple.substring(0, dollar);
    }

    /**
     * The name the criterion IDs are read from. A Vitest report prefixes the test's own name with its
     * {@code describe} names ({@code App > DFS-041.AC-10 shows ...}); the part after the last separator is the
     * test's own name (CCW-020 decision 6). A Java display name is used as it is.
     */
    String tracedName() {
        if (!isVitest()) return name;
        int last = name.lastIndexOf(VITEST_SEPARATOR);
        return last < 0 ? name : name.substring(last + VITEST_SEPARATOR.length());
    }

    /**
     * Whether this test case comes from the test file at repository-relative {@code path}: for Vitest, the path ends
     * with the report's project-relative one; for Java, the file's simple name is the outer class.
     */
    boolean fromFile(String path) {
        if (isVitest()) return path.equals(className) || path.endsWith("/" + className);
        String file = path.substring(path.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        return (dot < 0 ? file : file.substring(0, dot)).equals(outerClass());
    }

    String label() {
        if (isVitest()) return fileName() + " > " + name;
        return className.substring(className.lastIndexOf('.') + 1) + " > " + name;
    }

    private String fileName() {
        return className.substring(className.lastIndexOf('/') + 1);
    }

    /** Whether {@code type} is a stub's failure type: the Java exception, or a TypeScript {@code Error} (CCW-020). */
    static boolean isStubType(String type) {
        return STUB_EXCEPTION.equals(type) || TS_STUB_ERROR.equals(type);
    }

    /**
     * A failure raised by an architect's stub: exactly {@code UnsupportedOperationException("not implemented: ...")},
     * or a TypeScript {@code Error} with that message (CCW-020), either as the failure itself or as its root cause,
     * when a framework wrapped it (CCW-014).
     */
    boolean failedWithStub() {
        return outcome == Outcome.FAILED && (isStub(failureType, failureMessage) || isStub(rootType, rootMessage));
    }

    /**
     * {@link #failedWithStub()}, narrowed to ticket {@code id}'s own stub: the message is {@code not implemented: <id>},
     * optionally followed by text that does not continue the ID (CCW-015). A stub naming another ticket is not it.
     */
    boolean failedWithStubOf(String id) {
        return outcome == Outcome.FAILED
                && (isStubOf(failureType, failureMessage, id) || isStubOf(rootType, rootMessage, id));
    }

    private static boolean isStub(String type, String message) {
        return isStubType(type) && message.startsWith(STUB_PREFIX);
    }

    private static boolean isStubOf(String type, String message, String id) {
        String own = STUB_PREFIX + " " + id;
        if (!isStubType(type) || !message.startsWith(own)) return false;
        return message.length() == own.length() || !Character.isLetterOrDigit(message.charAt(own.length()));
    }

    /** True for a skip raised by an {@code Assumptions} check, false for a disabled test. */
    boolean skippedByAssumption() {
        return outcome == Outcome.SKIPPED
                && (skipType.endsWith("TestAbortedException") || skipType.endsWith("AssumptionViolatedException"));
    }

    /**
     * {@code SimpleType: first line of the message}: short, and never a stack trace. When the failure was wrapped,
     * {@code SimpleType; root cause SimpleType: first line} instead (CCW-014): a wrapper's own message is boilerplate
     * ("Failed to execute ApplicationRunner"), and dropping it keeps the root cause inside the verdict's line width.
     */
    String reason() {
        if (rootType.isEmpty()) return brief(failureType, failureMessage);
        return brief(failureType, "") + "; root cause " + brief(rootType, rootMessage);
    }

    private static String brief(String failureType, String failureMessage) {
        String type = failureType.substring(failureType.lastIndexOf('.') + 1);
        String first = failureMessage.lines().findFirst().orElse("").strip();
        if (first.codePointCount(0, first.length()) > 100) { // by code points: never split a surrogate pair
            first = first.substring(0, first.offsetByCodePoints(0, 97)) + "...";
        }
        if (type.isEmpty()) return first;
        return first.isEmpty() ? type : type + ": " + first;
    }
}
