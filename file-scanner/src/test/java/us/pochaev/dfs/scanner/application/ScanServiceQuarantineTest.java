package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.infra.port.LocationResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DFS-052.AC-4: {@code StartScan} refuses a location inside a quarantine folder, by the names of
 * its path alone. The resolver fake records every lookup, so "before the location is looked up on
 * disk" is observable; the repository fake exposes its records, so "no scan is created" is too.
 */
class ScanServiceQuarantineTest {

    /** An absolute path that exists nowhere; nothing here touches the disk. */
    private static final Path TMP = Path.of("").toAbsolutePath().resolve("dfs-052-tmp");

    @ParameterizedTest(name = "DFS-052.AC-4 {0} is refused")
    @ValueSource(strings = {"itself", "below", "second of two", "missing below"})
    @DisplayName("DFS-052.AC-4 A location in a quarantine is refused")
    void aLocationInAQuarantineIsRefused(String example) {
        Path named;
        List<Path> locations;
        switch (example) {
            case "itself" -> {
                named = TMP.resolve(".dfs-quarantine");
                locations = List.of(named);
            }
            case "below" -> {
                named = TMP.resolve(".dfs-quarantine/run-1/A");
                locations = List.of(named);
            }
            case "second of two" -> {
                named = TMP.resolve(".dfs-quarantine/run-1");
                locations = List.of(TMP.resolve("A"), named);
            }
            default -> {
                named = TMP.resolve(".dfs-quarantine/missing");
                locations = List.of(named);
            }
        }
        Fixture fixture = new Fixture();

        Result<ScanId> started = fixture.service.start(locations, ScanConfig.defaults());

        Result.Failure<ScanId> failure = assertInstanceOf(Result.Failure.class, started, String.valueOf(started));
        assertEquals(ErrorCode.LOCATION_IN_QUARANTINE, failure.code());
        assertTrue(failure.message().contains(named.toString()), failure.message());
        assertEquals(List.of(), fixture.resolver.lookups, "a location was looked up on disk");
        assertTrue(fixture.repository.records().isEmpty());
        assertEquals(0, fixture.executor.size());
    }

    @Test
    @DisplayName("DFS-052.AC-4 Another casing is not a quarantine")
    void anotherCasingIsNotAQuarantine() {
        Fixture fixture = new Fixture();
        Path location = TMP.resolve(".DFS-Quarantine/C");

        Result<ScanId> started = fixture.service.start(List.of(location), ScanConfig.defaults());

        assertInstanceOf(Result.Success.class, started, String.valueOf(started));
        assertEquals(1, fixture.repository.records().size());
    }

    private static final class Fixture {
        final RecordingResolver resolver = new RecordingResolver();
        final ScanServiceTest.ManualExecutor executor = new ScanServiceTest.ManualExecutor();
        final ScanServiceTest.ListingRepository repository = new ScanServiceTest.ListingRepository();
        final ScanService service = new ScanService(resolver, new ScanServiceTest.ScriptedRunner(), repository, executor);
    }

    private static final class RecordingResolver implements LocationResolver {
        final List<Path> lookups = new CopyOnWriteArrayList<>();

        @Override
        public Path realPath(Path location) throws IOException {
            lookups.add(location);
            return location;
        }
    }
}
