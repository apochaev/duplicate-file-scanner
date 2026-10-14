package us.pochaev.dfs.scanner.infra.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.LongConsumer;

/**
 * Hashes file content for the detection pipeline (CLAUDE.md decision 3): a cheap head-and-tail
 * sample for the partial-hash stage (DFS-002) and a full-content hash for confirmation (DFS-003).
 * <p>
 * Both methods return a digest as a lowercase hexadecimal string; two files are considered to match
 * at a stage exactly when their digests are {@link String#equals equal}. The real adapter uses
 * SHA-256 for both. Implementations must be safe to call from several threads at once.
 */
public interface ContentHasher {

    /** The most bytes {@link #fullHash(Path, LongConsumer)} reads between two progress reports: 8 MiB. */
    long PROGRESS_INTERVAL = 8L * 1024 * 1024;

    /**
     * Hashes the first {@code sampleSize} bytes and the last {@code sampleSize} bytes of
     * {@code file}, concatenated in that order (head, then tail). The file's size is not part of
     * the digest. If the file is shorter than {@code 2 * sampleSize} the head and tail overlap;
     * if it is shorter than {@code sampleSize} each of them is the whole file.
     *
     * @param file       the file to sample
     * @param sampleSize the number of bytes read from each end, positive
     * @return the lowercase hexadecimal digest of the sampled bytes
     * @throws IllegalArgumentException if {@code sampleSize} is not positive
     * @throws IOException              if {@code file} cannot be opened or read
     */
    String sampleHash(Path file, int sampleSize) throws IOException;

    /**
     * Hashes the whole content of {@code file}.
     *
     * @param file the file to hash
     * @return the lowercase hexadecimal digest of the file's content
     * @throws IOException if {@code file} cannot be opened or read
     */
    String fullHash(Path file) throws IOException;

    /**
     * Hashes the whole content of {@code file} like {@link #fullHash(Path)}, reporting progress to
     * {@code bytesRead}: the number of bytes of this file read so far, rising, at least once per
     * {@link #PROGRESS_INTERVAL} bytes read (DFS-024). Called on the hashing thread.
     * <p>
     * The default reports nothing and returns {@link #fullHash(Path)}, for hashers without
     * progress support.
     *
     * @param file      the file to hash
     * @param bytesRead receives the bytes read so far
     * @return the lowercase hexadecimal digest of the file's content
     * @throws IOException if {@code file} cannot be opened or read
     */
    default String fullHash(Path file, LongConsumer bytesRead) throws IOException {
        return fullHash(file);
    }
}
