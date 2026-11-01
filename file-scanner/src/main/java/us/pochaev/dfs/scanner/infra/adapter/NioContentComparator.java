package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.ContentComparator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
        try (FileChannel a = FileChannel.open(candidate, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             FileChannel b = FileChannel.open(keeper, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer bufA = ByteBuffer.allocate(chunkSize);
            ByteBuffer bufB = ByteBuffer.allocate(chunkSize);
            long done = 0;
            while (true) {
                if (cancelled.getAsBoolean()) {
                    return new Comparison.Cancelled();
                }
                bufA.clear();
                bufB.clear();
                int na = fill(a, bufA);
                int nb = fill(b, bufB);
                int common = Math.min(na, nb);
                for (int i = 0; i < common; i++) {
                    if (bufA.get(i) != bufB.get(i)) {
                        return new Comparison.Differs(done + i);
                    }
                }
                if (na != nb) {
                    return new Comparison.Differs(done + common);
                }
                if (na == 0) {
                    return new Comparison.Identical();
                }
                done += na;
                bytesCompared.accept(done);
                if (na < chunkSize) {
                    return new Comparison.Identical();
                }
            }
        }
    }

    private static int fill(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                break;
            }
        }
        return buffer.position();
    }
}
