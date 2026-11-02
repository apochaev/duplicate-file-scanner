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
import us.pochaev.dfs.server.api.ApiError;
import us.pochaev.dfs.server.scan.ScanRegistry;

/**
 * The overlaps API (DFS-048): a COMPLETED scan's folder summaries and folder pairs, ranked by DFS-046's overlap
 * analysis, filtered to any folder, and the pair of any two folders. Every path is under {@code /api}, so it needs the
 * session cookie (DFS-036); all three operations are GETs. The tag names the generated client's service
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
 * Stubs (design phase): every method body throws; the mappings, signatures and annotations are final, since
 * {@code ./gradlew :server:updateOpenApi} builds the committed spec from them.
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
        throw new UnsupportedOperationException("not implemented: DFS-048");
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
        throw new UnsupportedOperationException("not implemented: DFS-048");
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
        throw new UnsupportedOperationException("not implemented: DFS-048");
    }
}
