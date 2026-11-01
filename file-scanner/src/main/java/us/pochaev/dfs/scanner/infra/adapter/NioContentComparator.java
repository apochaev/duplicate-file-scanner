package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.ContentComparator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * {@link ContentComparator} on two {@code FileChannel}s opened with {@code READ} and {@code NOFOLLOW_LINKS}
 * (DFS-034 decision 4), read one chunk of each at a time. Holds only its chunk size; buffers are per call.
 */
public final class NioContentComparator implements ContentComparator {

    private final int chunkSize;

    /** A comparator reading {@link #DEFAULT_CHUNK_SIZE} bytes of each file at a time. */
    public NioContentComparator() {
        this(DEFAULT_CHUNK_SIZE);
    }

    /**
     * @param chunkSize the bytes read from each file per chunk, positive (small values let tests span several chunks)
     * @throws IllegalArgumentException if {@code chunkSize} is not positive
     */
    public NioContentComparator(int chunkSize) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
        }
        this.chunkSize = chunkSize;
    }

    @Override
    public Comparison compare(Path candidate, Path keeper, LongConsumer bytesCompared, BooleanSupplier cancelled)
            throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-034");
    }
}
