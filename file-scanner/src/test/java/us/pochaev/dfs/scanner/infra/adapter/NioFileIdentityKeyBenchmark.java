package us.pochaev.dfs.scanner.infra.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reproduces the timing DFS-026's Definition of done requires: {@link NioFileIdentity#key} over
 * 10,000 real files in a temporary directory (on Windows, one {@code CreateFileW} plus
 * {@code GetFileInformationByHandleEx(FileIdInfo)} per file).
 *
 * <p>Deliberately not a {@code @Test} (and not {@code @Disabled}, which {@code tools/story-gate}'s
 * {@code DisabledCheck} rejects): a manual benchmark, following {@code SizeIndexHeapBenchmark}. Run
 * it directly against the compiled classes, with native access enabled, e.g. from the repo root
 * after {@code ./gradlew :file-scanner:testClasses}:
 *
 * <pre>
 * java --enable-native-access=ALL-UNNAMED \
 *   -cp file-scanner/build/classes/java/main:file-scanner/build/classes/java/test \
 *   us.pochaev.dfs.scanner.infra.adapter.NioFileIdentityKeyBenchmark
 * </pre>
 *
 * (use {@code ;} as the classpath separator on Windows). It prints the time of a first (cold
 * handle-linking included) and a second pass, and checks that all 10,000 keys are distinct.
 */
final class NioFileIdentityKeyBenchmark {

    private static final int FILE_COUNT = 10_000;
    private static final int FILE_SIZE = 4_096;

    private NioFileIdentityKeyBenchmark() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("dfs-026-key-benchmark");
        try {
            byte[] content = new byte[FILE_SIZE];
            List<Path> files = new ArrayList<>(FILE_COUNT);
            for (int i = 0; i < FILE_COUNT; i++) {
                Path sub = dir.resolve("d%02d".formatted(i % 50));
                Files.createDirectories(sub);
                Path file = sub.resolve("._f%05d".formatted(i));
                Files.write(file, content);
                files.add(file);
            }
            files.sort(Comparator.naturalOrder());

            NioFileIdentity identity = new NioFileIdentity();
            for (int pass = 1; pass <= 2; pass++) {
                Set<Object> keys = new HashSet<>();
                long start = System.nanoTime();
                for (Path file : files) {
                    keys.add(identity.key(file));
                }
                long elapsed = System.nanoTime() - start;
                System.out.printf("pass %d: %,d keys in %,.1f ms (%.1f us/file), %,d distinct%n",
                        pass, files.size(), elapsed / 1e6, elapsed / 1e3 / files.size(), keys.size());
                if (keys.size() != FILE_COUNT) {
                    throw new IllegalStateException("expected " + FILE_COUNT + " distinct keys");
                }
            }
        } finally {
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
