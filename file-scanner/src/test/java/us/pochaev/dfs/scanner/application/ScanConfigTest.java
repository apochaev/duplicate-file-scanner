package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link ScanConfig} in {@code domain.model}: the pipeline's settings and their defaults
 * (DFS-005.AC-7, DFS-005.AC-8).
 * <p>
 * Every test here constructs a {@link ScanConfig}, whose compact constructor and whose
 * {@link ScanConfig#defaults()} are currently stubbed to throw
 * {@code UnsupportedOperationException("not implemented: DFS-005")} unconditionally, before any
 * value is inspected. That is why every test below fails red for that one reason today: the
 * "invalid value" tests use a try/catch rather than {@code assertThrows}, so the stub's exception
 * propagates uncaught instead of being reported as this test's own {@code AssertionFailedError}
 * (test-guide.md's L-013 warning).
 */
class ScanConfigTest {

    @Test
    @DisplayName("DFS-005.AC-7 The default config matches the stages' own defaults")
    void defaultConfigMatchesStagesOwnDefaults() {
        ScanConfig defaults = ScanConfig.defaults();

        assertEquals(new ScanConfig(1, 65536, 131072, true), defaults);
        assertEquals(SizeIndex.DEFAULT_MIN_SIZE, defaults.minSize());
        assertEquals(PartialHashFilter.DEFAULT_SAMPLE_SIZE, defaults.sampleSize());
        assertEquals(PartialHashFilter.DEFAULT_SKIP_THRESHOLD, defaults.partialHashThreshold());
    }

    @Test
    @DisplayName("DFS-027.AC-5 The default config has the default excludes on, matching the filter's own default")
    void defaultConfigHasDefaultExcludesOn() {
        ScanConfig defaults = ScanConfig.defaults();

        assertTrue(defaults.defaultExcludes());
        assertEquals(DefaultExcludeFilter.DEFAULT_ENABLED, defaults.defaultExcludes());
        assertEquals(new ScanConfig(1, 65536, 131072, true), defaults);
    }

    @Test
    @DisplayName("DFS-005.AC-8 A minSize below 1 is rejected")
    void minSizeBelowOneIsRejected() {
        try {
            new ScanConfig(0, 65536, 131072, true);
            fail("expected IllegalArgumentException for minSize 0");
        } catch (IllegalArgumentException expected) {
            // expected once implemented
        }
    }

    @Test
    @DisplayName("DFS-005.AC-8 A sampleSize of zero or less is rejected")
    void sampleSizeOfZeroOrLessIsRejected() {
        try {
            new ScanConfig(1, 0, 131072, true);
            fail("expected IllegalArgumentException for sampleSize 0");
        } catch (IllegalArgumentException expected) {
            // expected once implemented
        }
    }

    @Test
    @DisplayName("DFS-005.AC-8 A partialHashThreshold of zero or less is rejected")
    void partialHashThresholdOfZeroOrLessIsRejected() {
        try {
            new ScanConfig(1, 65536, 0, true);
            fail("expected IllegalArgumentException for partialHashThreshold 0");
        } catch (IllegalArgumentException expected) {
            // expected once implemented
        }
    }
}
