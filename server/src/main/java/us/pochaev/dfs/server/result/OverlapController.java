package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.server.analysis.FolderGroups;
import us.pochaev.dfs.server.analysis.OverlapAnalysis;
import us.pochaev.dfs.server.api.ApiError;
import us.pochaev.dfs.server.api.ApiException;
import us.pochaev.dfs.server.scan.ScanRegistry;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * The overlaps API (DFS-048): a COMPLETED scan's folder summaries and folder pairs, ranked by DFS-046's overlap
 * analysis, filtered to any folder, and the pair of any two folders. Every path is under {@code /api}, so it needs the
 * session cookie (DFS-036); all four operations are GETs. The tag names the generated client's service
 * ({@code OverlapsService}), each operation ID its method (endpoint archetype, checklist item 6).
 * <p>
 * Checks run in this order (decision 4, DFS-047 decision 5): the scan ID ({@code 404 UNKNOWN_SCAN}, also for a
 * malformed one) and completion ({@code 409 SCAN_NOT_COMPLETED} with the core's message), both from
 * {@code ScanRegistry.completedState(id, OverlapState.class, r -> new OverlapState(r, OverlapAnalysis::of))}; then,
 * for {@code pair}, {@code a} and {@code b} present ({@code 400 MISSING_FOLDER}); then each path as DFS-047's
 * {@code ResultController} checks it ({@code 400 RELATIVE_PATH}, then {@code 403 PATH_OUTSIDE_ROOTS}, an unparsable
 * path included, through {@link OverlapState#insideRoots}); then paging ({@code 400 INVALID_PAGE}) or, for
 * {@code pair}, nesting ({@code 400 NESTED_FOLDERS}). Only then is {@link OverlapState#index()} asked, so a refused
 * request never starts the analysis.
 * <p>
 * The mappings, signatures and annotations shape the committed spec ({@code ./gradlew :server:updateOpenApi}).
 */
@RestController
@RequestMapping(path = "/api/scans", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "overlaps")
class OverlapController {

    private static final String NO_SUCH_SCAN = "No such scan in this server run (UNKNOWN_SCAN)";
    private static final String NOT_COMPLETED = "The scan is not COMPLETED (SCAN_NOT_COMPLETED)";
    private static final String RELATIVE = "The path is not absolute (RELATIVE_PATH)";
    private static final String OUTSIDE = "The path lies outside every root of the scan (PATH_OUTSIDE_ROOTS)";
    private static final String BAD_PAGE = "page below 0 or size outside 1 to 500 (INVALID_PAGE)";

    private final ScanRegistry registry;

    OverlapController(ScanRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/{id}/summaries")
    @Operation(operationId = "listSummaries", summary = "A page of a completed scan's folder summaries, ranked by bytes"
            + " with a copy elsewhere; with under, those strictly inside it, plus under's own summary")
    @ApiResponse(responseCode = "200", description = "The page; past the end it has no items and the same total",
            content = @Content(schema = @Schema(implementation = SummaryPage.class)))
    @ApiResponse(responseCode = "400", description = RELATIVE + "; or " + BAD_PAGE,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = OUTSIDE,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_SCAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = NOT_COMPLETED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    SummaryPage listSummaries(
            @PathVariable String id,
            @Parameter(description = "An absolute folder inside one of the scan's roots; without it, every summary")
            @RequestParam(required = false) String under,
            @Parameter(description = "The page, from 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Summaries per page, 1 to 500")
            @RequestParam(defaultValue = "50") int size) {
        // Take the ID as a String: a malformed one must be the same 404 as an unknown one (checklist item 4).
        OverlapState state = state(id);
        Path path = path(state::insideRoots, under);
        checkPage(page, size);
        return state.index().summaries(path, page, size);
    }

    @GetMapping("/{id}/pairs")
    @Operation(operationId = "listPairs", summary = "A page of a completed scan's folder pairs, ranked by bytes with a"
            + " copy; with under, those with at least one side inside it, whatever the other side")
    @ApiResponse(responseCode = "200", description = "The page; past the end it has no items and the same total",
            content = @Content(schema = @Schema(implementation = PairPage.class)))
    @ApiResponse(responseCode = "400", description = RELATIVE + "; or " + BAD_PAGE,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = OUTSIDE,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_SCAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = NOT_COMPLETED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    PairPage listPairs(
            @PathVariable String id,
            @Parameter(description = "An absolute folder inside one of the scan's roots; without it, every pair")
            @RequestParam(required = false) String under,
            @Parameter(description = "The page, from 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Pairs per page, 1 to 500")
            @RequestParam(defaultValue = "50") int size) {
        OverlapState state = state(id);
        Path path = path(state::insideRoots, under);
        checkPage(page, size);
        return state.index().pairs(path, page, size);
    }

    @GetMapping("/{id}/pair")
    @Operation(operationId = "getPair", summary = "The overlap of two folders inside a completed scan's roots, each"
            + " counted recursively, a before b by path whatever order they were given in")
    @ApiResponse(responseCode = "200", description = "The pair; a folder the scan holds nothing for is a side of zeros",
            content = @Content(schema = @Schema(implementation = FolderPairView.class)))
    @ApiResponse(responseCode = "400", description = "a or b absent (MISSING_FOLDER); " + RELATIVE
            + "; or the folders are equal or one contains the other (NESTED_FOLDERS)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = OUTSIDE,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_SCAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = NOT_COMPLETED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    FolderPairView getPair(
            @PathVariable String id,
            @Parameter(description = "An absolute folder inside one of the scan's roots; required, checked by the"
                    + " server (MISSING_FOLDER)")
            @RequestParam(required = false) String a,
            @Parameter(description = "Another absolute folder inside one of the scan's roots, neither equal to a nor"
                    + " containing or inside it; required, checked by the server (MISSING_FOLDER)")
            @RequestParam(required = false) String b) {
        OverlapState state = state(id);
        if (a == null || b == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_FOLDER", "Both a and b are required");
        }
        Path pa = path(state::insideRoots, a);
        Path pb = path(state::insideRoots, b);
        return state.index().pair(pa, pb);
    }

    /**
     * DFS-067: a page of the scan's folder groups. Checks, in order (decision 3): the scan ID and completion through
     * {@code ScanRegistry.completedState(id, GroupState.class, r -> new GroupState(r, FolderGroups::of))} ({@code 404}
     * {@code UNKNOWN_SCAN}, {@code 409} {@code SCAN_NOT_COMPLETED}); {@code under} as for {@code listSummaries}, through
     * {@link GroupState#insideRoots}; paging ({@code 400 INVALID_PAGE}); a negative {@code minBytes}
     * ({@code 400 INVALID_MIN_BYTES}); only then {@link GroupState#index()}, so a refused request builds nothing.
     */
    @GetMapping("/{id}/groups")
    @Operation(operationId = "listGroups", summary = "A page of a completed scan's folder groups: duplicate sets whose"
            + " copies sit directly in the same folders, ranked by reclaimable bytes; with under, those with a folder"
            + " at or inside it; groups below minBytes are left out and counted")
    @ApiResponse(responseCode = "200", description = "The page; past the end it has no items and the same total",
            content = @Content(schema = @Schema(implementation = GroupPage.class)))
    @ApiResponse(responseCode = "400", description = RELATIVE + "; " + BAD_PAGE
            + "; or minBytes below 0 (INVALID_MIN_BYTES)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = OUTSIDE,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_SCAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = NOT_COMPLETED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    GroupPage listGroups(
            @PathVariable String id,
            @Parameter(description = "An absolute folder inside one of the scan's roots; without it, every group")
            @RequestParam(required = false) String under,
            @Parameter(description = "Groups reclaiming fewer bytes than this are left out of items and counted in"
                    + " hiddenSmall; 0 or more, 0 answers every group")
            @RequestParam(defaultValue = "1048576") long minBytes,
            @Parameter(description = "The page, from 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Groups per page, 1 to 500")
            @RequestParam(defaultValue = "50") int size) {
        GroupState state = switch (registry.completedState(id, GroupState.class,
                r -> new GroupState(r, FolderGroups::of))) {
            case Result.Success<GroupState> ok -> ok.value();
            case Result.Failure<GroupState> f -> throw new ApiException(
                    f.code() == ErrorCode.UNKNOWN_SCAN ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                    f.code().name(), f.message());
        };
        Path path = path(state::insideRoots, under);
        checkPage(page, size);
        if (minBytes < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MIN_BYTES", "minBytes must be 0 or more");
        }
        return state.index().page(path, minBytes, page, size);
    }

    private OverlapState state(String id) {
        return switch (registry.completedState(id, OverlapState.class, r -> new OverlapState(r, OverlapAnalysis::of))) {
            case Result.Success<OverlapState> ok -> ok.value();
            case Result.Failure<OverlapState> f -> throw new ApiException(
                    f.code() == ErrorCode.UNKNOWN_SCAN ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                    f.code().name(), f.message());
        };
    }

    private static void checkPage(int page, int size) {
        if (page < 0 || size < 1 || size > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE",
                    "page must be 0 or more and size 1 to 500");
        }
    }

    /** The requested path, normalized; null when none was given. */
    private static Path path(Predicate<Path> insideRoots, String value) {
        if (value == null) {
            return null;
        }
        Path path;
        try {
            path = Path.of(value).normalize();
        } catch (InvalidPathException e) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PATH_OUTSIDE_ROOTS", "The path lies outside every root");
        }
        if (value.isBlank() || !path.isAbsolute()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RELATIVE_PATH", "The path is not absolute: " + value);
        }
        if (!insideRoots.test(path)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PATH_OUTSIDE_ROOTS",
                    "The path lies outside every root of the scan: " + value);
        }
        return path;
    }
}
