package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.ContentHasher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 {@link ContentHasher} on NIO file channels. Stateless: a fresh
 * {@link java.security.MessageDigest} per call, so one instance is safe to share across threads
 * and concurrent scans.
 */
public final class NioContentHasher implements ContentHasher {

    /**
     * Reads the head and tail with positional reads on a {@link java.nio.channels.FileChannel},
     * using the channel's size at read time.
     */
    @Override
    public String sampleHash(Path file, int sampleSize) throws IOException {
        Objects.requireNonNull(file, "file");
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("sampleSize must be positive: " + sampleSize);
        }
        MessageDigest digest = newSha256();
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            long headLength = Math.min(sampleSize, size);
            digest.update(readAt(channel, 0, headLength));

            long tailLength = Math.min(sampleSize, size);
            long tailStart = Math.max(0, size - tailLength);
            digest.update(readAt(channel, tailStart, tailLength));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Streams the whole file through SHA-256 as it is at read time; the file's size is not
     * checked against any earlier observation.
     */
    @Override
    public String fullHash(Path file) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-003");
        // implementer: Objects.requireNonNull(file, "file"); a fresh newSha256(); read the file
        // in fixed-size chunks (for example a 64 KB buffer over a FileChannel or InputStream,
        // never Files.readAllBytes: files can be gigabytes) until end of stream, updating the
        // digest; return HexFormat.of().formatHex(digest.digest()).
    }

    private static byte[] readAt(FileChannel channel, long position, long length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate((int) length);
        long pos = position;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, pos);
            if (read < 0) {
                break;
            }
            pos += read;
        }
        return buffer.array();
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
