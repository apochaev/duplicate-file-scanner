/**
 * One test case from a JUnit XML report. For a failure, {@code failureType} is the exception class and
 * {@code failureMessage} its message without the leading class name. For a skip, {@code skipType} is the
 * exception behind it (an assumption failure), or empty when the test was disabled.
 */
record TestResult(String className, String name, Outcome outcome, String failureType, String failureMessage,
                  String skipType) {

    enum Outcome { PASSED, FAILED, SKIPPED }

    static final String STUB_EXCEPTION = "java.lang.UnsupportedOperationException";
    static final String STUB_PREFIX = "not implemented:";

    static TestResult passed(String className, String name) {
        return new TestResult(className, name, Outcome.PASSED, "", "", "");
    }

    static TestResult failed(String className, String name, String type, String message) {
        return new TestResult(className, name, Outcome.FAILED, type, message, "");
    }

    static TestResult skipped(String className, String name, String skipType) {
        return new TestResult(className, name, Outcome.SKIPPED, "", "", skipType);
    }

    /** The class without its package, and without any outer class ({@code a.b.Outer$Inner} gives {@code Outer}). */
    String outerClass() {
        String simple = className.substring(className.lastIndexOf('.') + 1);
        int dollar = simple.indexOf('$');
        return dollar < 0 ? simple : simple.substring(0, dollar);
    }

    String label() {
        return className.substring(className.lastIndexOf('.') + 1) + " > " + name;
    }

    /** A failure raised by an architect's stub: exactly {@code UnsupportedOperationException("not implemented: ...")}. */
    boolean failedWithStub() {
        return outcome == Outcome.FAILED && STUB_EXCEPTION.equals(failureType)
                && failureMessage.startsWith(STUB_PREFIX);
    }

    /** True for a skip raised by an {@code Assumptions} check, false for a disabled test. */
    boolean skippedByAssumption() {
        return outcome == Outcome.SKIPPED
                && (skipType.endsWith("TestAbortedException") || skipType.endsWith("AssumptionViolatedException"));
    }

    /** {@code SimpleType: first line of the message}: short, and never a stack trace. */
    String reason() {
        String type = failureType.substring(failureType.lastIndexOf('.') + 1);
        String first = failureMessage.lines().findFirst().orElse("").strip();
        if (first.codePointCount(0, first.length()) > 100) { // by code points: never split a surrogate pair
            first = first.substring(0, first.offsetByCodePoints(0, 97)) + "...";
        }
        if (type.isEmpty()) return first;
        return first.isEmpty() ? type : type + ": " + first;
    }
}
