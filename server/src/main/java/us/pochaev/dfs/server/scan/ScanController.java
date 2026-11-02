package us.pochaev.dfs.server.scan;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.server.api.ApiError;
import us.pochaev.dfs.server.api.ApiException;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The scan API (DFS-040): start, list, get, cancel and follow the scans of this server run. Every path is under
 * {@code /api}, so it needs the session cookie, and both POSTs need a matching {@code Origin} (DFS-036).
 */
@RestController
@RequestMapping(path = "/api/scans", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "scans")
class ScanController {

    private static final String NO_SUCH_SCAN = "No such scan in this server run";

    private final ScanRegistry registry;
    private final ScanEventStreams streams;

    ScanController(ScanRegistry registry, ScanEventStreams streams) {
        this.registry = registry;
        this.streams = streams;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "startScan", summary = "Start a scan of absolute folders")
    @ApiResponse(responseCode = "201", description = "Started; Location names the scan",
            content = @Content(schema = @Schema(implementation = ScanEntry.class)))
    @ApiResponse(responseCode = "400", description = "Refused; nothing was started",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    ResponseEntity<ScanEntry> startScan(@RequestBody StartScanRequest request) {
        List<String> locations = request.locations() == null ? List.of() : request.locations();
        List<Path> paths = new ArrayList<>();
        for (String location : locations) {
            Path path;
            try {
                path = Path.of(location);
            } catch (InvalidPathException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.LOCATION_NOT_FOUND.name(),
                        "Not a valid path: " + location);
            }
            if (!path.isAbsolute()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "RELATIVE_LOCATION",
                        "Give an absolute path, not " + location);
            }
            paths.add(path);
        }
        ScanConfig defaults = ScanConfig.defaults();
        long minSize = request.minSize() == null ? defaults.minSize() : request.minSize();
        if (minSize < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MIN_SIZE", "minSize must be at least 1: " + minSize);
        }
        boolean excludes = request.defaultExcludes() == null ? defaults.defaultExcludes() : request.defaultExcludes();
        ScanConfig config = new ScanConfig(minSize, defaults.sampleSize(), defaults.partialHashThreshold(), excludes);

        return switch (registry.start(locations, paths, config)) {
            case Result.Success<ScanEntry> s ->
                    ResponseEntity.created(URI.create("/api/scans/" + s.value().id())).body(s.value());
            case Result.Failure<ScanEntry> f -> throw new ApiException(
                    f.code() == ErrorCode.EXECUTOR_REJECTED ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST,
                    f.code().name(), f.message());
        };
    }

    @GetMapping
    @Operation(operationId = "listScans", summary = "The scans of this server run, newest first")
    List<ScanEntry> listScans() {
        return registry.list();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getScan", summary = "One scan and its progress")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ScanEntry.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_SCAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    ScanEntry getScan(@PathVariable String id) {
        return registry.find(id).orElseThrow(() -> ApiException.unknownScan(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(operationId = "cancelScan", summary = "Request that a scan stop")
    @ApiResponse(responseCode = "200", description = "The scan after the request",
            content = @Content(schema = @Schema(implementation = ScanEntry.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_SCAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    ScanEntry cancelScan(@PathVariable String id) {
        return registry.cancel(id).orElseThrow(() -> ApiException.unknownScan(id));
    }

    @GetMapping(path = "/{id}/events", produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(operationId = "streamScanEvents", summary = "Server-Sent Events: a progress event with a ScanProgress"
            + " at most every 250 ms, ending with the terminal status")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
            schema = @Schema(implementation = ScanProgress.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_SCAN, content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
    SseEmitter streamScanEvents(@PathVariable String id) {
        ScanProgress current = registry.progress(id).orElseThrow(() -> ApiException.unknownScan(id));
        return streams.open(id, current);
    }
}
