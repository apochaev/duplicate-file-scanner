package us.pochaev.dfs.scanner.domain.model;

/**
 * Settings of one scan (DFS-005). A plain record so {@code @ConfigurationProperties} can map onto
 * it later (CLAUDE.md, Spring-friendliness rules); later tickets add their own fields.
 *
 * @param minSize              files smaller than this many bytes never reach a duplicate set; at
 *                             least 1, so empty files are always excluded
 * @param sampleSize           bytes sampled from each end of a file by the partial hash; positive
 * @param partialHashThreshold size in bytes at or under which the partial hash is skipped; positive
 */
public record ScanConfig(long minSize, int sampleSize, long partialHashThreshold) {

    /**
     * @throws IllegalArgumentException if {@code minSize < 1}, or {@code sampleSize} or
     *                                  {@code partialHashThreshold} is not positive
     */
    public ScanConfig {
        throw new UnsupportedOperationException("not implemented: DFS-005");
        // implementer: reject minSize < 1, sampleSize <= 0 and partialHashThreshold <= 0 with
        // IllegalArgumentException (DFS-005.AC-8). Deliberately not in the stub, so an
        // invalid-argument test fails red instead of passing against it (L-013).
    }

    /**
     * The defaults, {@code (1, 65536, 131072)}: the same values as {@code SizeIndex.DEFAULT_MIN_SIZE},
     * {@code PartialHashFilter.DEFAULT_SAMPLE_SIZE} and {@code PartialHashFilter.DEFAULT_SKIP_THRESHOLD}
     * ({@code domain.model} cannot reference {@code application}, so the values are stated here and a
     * test pins them equal).
     */
    public static ScanConfig defaults() {
        throw new UnsupportedOperationException("not implemented: DFS-005");
    }
}
