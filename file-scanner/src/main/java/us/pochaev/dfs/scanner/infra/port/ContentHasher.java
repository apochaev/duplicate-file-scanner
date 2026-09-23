package us.pochaev.dfs.scanner.infra.port;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Hashes file content for the detection pipeline (CLAUDE.md decision 3): a cheap head-and-tail
 * sample for the partial-hash stage (DFS-002) and a full-content hash for confirmation (DFS-003).
 * <p>
 * Both methods return a digest as a lowercase hexadecimal string; two files are considered to match
 * at a stage exactly when their digests are {@link String#equals equal}. The real adapter uses
 * SHA-256 for both. Implementations must be safe to call from several threads at once.
 */
public interface ContentHasher {

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
}
