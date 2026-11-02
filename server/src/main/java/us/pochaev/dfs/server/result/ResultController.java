package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

import java.util.List;

/**
 * The result API (DFS-047): a COMPLETED scan's folder tree, one level at a time, and its duplicate sets under any
 * folder, largest reclaimable first. Every path is under {@code /api}, so it needs the session cookie (DFS-036); both
 * operations are GETs. A {@code parent} or {@code under} path must be absolute and inside one of the scan's roots
 * (U10). The tag names the generated client's service ({@code ResultsService}), each operation ID its method
 * (endpoint archetype, checklist item 6).
 * <p>
 * Checks run in this order (decision 5): the scan ID ({@code 404 UNKNOWN_SCAN}, also for a malformed one), completion
 * ({@code 409 SCAN_NOT_COMPLETED} with the core's message), the path ({@code 400 RELATIVE_PATH}, then
 * {@code 403 PATH_OUTSIDE_ROOTS}), then paging ({@code 400 INVALID_PAGE}).
 * <p>
 * Stubs (design phase): every method body throws; the mappings, signatures and annotations are final, since
 * {@code ./gradlew :server:updateOpenApi} builds the committed spec from them.
 */
@RestController
@RequestMapping(path = "/api/scans", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "results")
class ResultController {

    private static final String NO_SUCH_SCAN = "No such scan in this server run (UNKNOWN_SCAN)";
    private static final String NOT_COMPLETED = "The scan is not COMPLETED (SCAN_NOT_COMPLETED)";
    private static final String RELATIVE = "The path is not absolute (RELATIVE_PATH)";
    private static final String OUTSIDE = "The path lies outside every root of the scan (PATH_OUTSIDE_ROOTS)";

    private final ScanRegistry registry;

    ResultController(ScanRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/{id}/folders")
    @Operation(operationId = "listFolders", summary = "A completed scan's roots, or the immediate subfolders of parent"
            + " that hold counted files, ordered by name, with subtree and direct totals")
    @ApiResponse(responseCode = "200", description = "One level of the tree; empty when the scan holds nothing there",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = FolderNode.class))))
    @ApiResponse(responseCode = "400", description = RELATIVE,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = OUTSIDE,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_SCAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = NOT_COMPLETED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    List<FolderNode> listFolders(
            @PathVariable String id,
            @Parameter(description = "An absolute folder inside one of the scan's roots; without it, the roots")
            @RequestParam(required = false) String parent) {
        // Take the ID as a String: a malformed one must be the same 404 as an unknown one (checklist item 4).
        throw new UnsupportedOperationException("not implemented: DFS-047");
    }

    @GetMapping("/{id}/sets")
    @Operation(operationId = "listSets", summary = "A page of a completed scan's duplicate sets with a path under the"
            + " folder, largest reclaimable first, each with every copy")
    @ApiResponse(responseCode = "200", description = "The page; past the end it has no items and the same totals",
            content = @Content(schema = @Schema(implementation = SetPage.class)))
    @ApiResponse(responseCode = "400", description = RELATIVE + "; or page below 0 or size outside 1 to 500"
            + " (INVALID_PAGE)", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = OUTSIDE,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_SCAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = NOT_COMPLETED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    SetPage listSets(
            @PathVariable String id,
            @Parameter(description = "An absolute folder inside one of the scan's roots; without it, every set")
            @RequestParam(required = false) String under,
            @Parameter(description = "The page, from 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sets per page, 1 to 500")
            @RequestParam(defaultValue = "50") int size) {
        throw new UnsupportedOperationException("not implemented: DFS-047");
    }
}
