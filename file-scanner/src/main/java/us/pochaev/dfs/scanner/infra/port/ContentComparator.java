package us.pochaev.dfs.scanner.infra.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Compares two files' content byte for byte in chunks (CLAUDE.md decision 4, DFS-034 decision 4), opening neither
 * through a link. Implementations must be safe to call from several threads at once.
 */
public interface ContentComparator {

    /** The default chunk size: 1 MiB. */
    int DEFAULT_CHUNK_SIZE = 1024 * 1024;

    /**
     * Reads {@code candidate} and {@code keeper} side by side, one chunk of each at a time. Before reading each chunk
     * it reads {@code cancelled} and returns {@link Comparison.Cancelled} if it is true; after comparing each chunk it
     * reports to {@code bytesCompared} the bytes of this pair compared so far (rising). It stops at the first
     * differing byte. A file that ends before the other differs at the offset where it ends.
     *
     * @return {@link Comparison.Identical}, {@link Comparison.Differs} with the zero-based offset of the first
     *         differing byte, or {@link Comparison.Cancelled}
     * @throws IOException if either file cannot be opened (a link included: neither is followed) or read
     */
    Comparison compare(Path candidate, Path keeper, LongConsumer bytesCompared, BooleanSupplier cancelled)
            throws IOException;

    /** The result of one {@link #compare}. */
    sealed interface Comparison {

        /** Every byte matched and both files ended together. */
        record Identical() implements Comparison {
        }

        /** The first difference is at the zero-based byte {@code offset}. */
        record Differs(long offset) implements Comparison {
        }

        /** The cancel signal stopped the compare before its end. */
        record Cancelled() implements Comparison {
        }
    }
}
