package us.pochaev.dfs.scanner.domain.model;

/**
 * Settings of one scan (DFS-005). A plain record so {@code @ConfigurationProperties} can map onto
 * it later (CLAUDE.md, Spring-friendliness rules); later tickets add their own fields.
 *
 * @param minSize              files smaller than this many bytes never reach a duplicate set; at
 *                             least 1, so empty files are always excluded
 * @param sampleSize           bytes sampled from each end of a file by the partial hash; positive
 * @param partialHashThreshold size in bytes at or under which the partial hash is skipped; positive
 * @param defaultExcludes      whether the built-in list of operating-system metadata names
 *                             ({@code .DS_Store} and {@code ._*}, matched case-sensitively) is left
 *                             out of the scan and counted in {@link ScanResult#excluded()} (DFS-027);
 *                             the names themselves live with the filter in {@code application}; also
 *                             whether the walker skips the operating-system folders ({@code $RECYCLE.BIN},
 *                             {@code .Trashes}, {@code lost+found} and the like) below a scan root,
 *                             reporting each as skipped with {@code SYSTEM_FOLDER} (DFS-061)
 */
public record ScanConfig(long minSize, int sampleSize, long partialHashThreshold, boolean defaultExcludes) {

    /**
     * @throws IllegalArgumentException if {@code minSize < 1}, or {@code sampleSize} or
     *                                  {@code partialHashThreshold} is not positive
     */
    public ScanConfig {
        if (minSize < 1) {
            throw new IllegalArgumentException("minSize must be at least 1: " + minSize);
        }
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("sampleSize must be positive: " + sampleSize);
        }
        if (partialHashThreshold <= 0) {
            throw new IllegalArgumentException("partialHashThreshold must be positive: " + partialHashThreshold);
        }
    }

    /**
     * The defaults, {@code (1, 65536, 131072, true)}: the same values as {@code SizeIndex.DEFAULT_MIN_SIZE},
     * {@code PartialHashFilter.DEFAULT_SAMPLE_SIZE}, {@code PartialHashFilter.DEFAULT_SKIP_THRESHOLD} and
     * {@code DefaultExcludeFilter.DEFAULT_ENABLED} ({@code domain.model} cannot reference
     * {@code application}, so the values are stated here and a test pins them equal).
     */
    public static ScanConfig defaults() {
        return new ScanConfig(1, 65536, 131072, true);
    }
}
