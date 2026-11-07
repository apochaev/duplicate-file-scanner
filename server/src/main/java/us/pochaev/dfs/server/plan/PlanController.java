package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.server.api.ApiException;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import us.pochaev.dfs.scanner.domain.usecases.query.PreviewDeletionPlan;
import us.pochaev.dfs.server.api.ApiError;
import us.pochaev.dfs.server.scan.ScanRegistry;

/**
 * The plan preview API (DFS-054): preview a whole deletion-plan request on a COMPLETED scan, keep the frozen plan for
 * this server run, and read back its summary and its operations and conflicts, paged, largest first. Every path is
 * under {@code /api}, so it needs the session cookie, and the POST needs a matching {@code Origin} (DFS-036). The tag
 * names the generated client's service ({@code PlansService}), each operation ID its method (endpoint archetype,
 * checklist item 6).
 * <p>
 * Checks run in this order (decision 5, AC-11): the scan ID ({@code 404 UNKNOWN_SCAN}, also for a malformed one) and
 * completion ({@code 409 SCAN_NOT_COMPLETED} with the core's message), both from
 * {@code ScanRegistry.completedState(id, PlanStore.class, PlanStore::new)}; then, for the POST, every request path
 * ({@link PlanRequests#toCore}: {@code 400 RELATIVE_PATH}, then {@code 403 PATH_OUTSIDE_ROOTS}, field named) and the
 * core's refusals ({@code 400} with the core's code and message); for the GETs, the plan ID
 * ({@code 404 UNKNOWN_PLAN}), then paging ({@code 400 INVALID_PAGE}). A refused POST keeps no plan and answers no
 * {@code Location}.
 */
@RestController
@RequestMapping(path = "/api/scans", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "plans")
class PlanController {

    private static final String NO_SUCH_SCAN = "No such scan in this server run (UNKNOWN_SCAN)";
    private static final String NOT_COMPLETED = "The scan is not COMPLETED (SCAN_NOT_COMPLETED)";
    private static final String NO_SUCH_PLAN = "No plan with this ID was previewed for this scan in this server run"
            + " (UNKNOWN_PLAN); " + NO_SUCH_SCAN;
    private static final String BAD_PAGE = "page below 0 or size outside 1 to 500 (INVALID_PAGE)";

    private final ScanRegistry registry;
    private final PreviewDeletionPlan preview;

    PlanController(ScanRegistry registry, PreviewDeletionPlan preview) {
        this.registry = registry;
        this.preview = preview;
    }

    @PostMapping(path = "/{id}/plans", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "previewPlan", summary = "Preview rules, exceptions and single-set picks on a completed"
            + " scan; the frozen plan is kept for this server run and the same request gives the same plan ID")
    @ApiResponse(responseCode = "201", description = "Previewed and kept; Location names the plan",
            content = @Content(schema = @Schema(implementation = PlanSummary.class)))
    @ApiResponse(responseCode = "400", description = "A path is not absolute (RELATIVE_PATH, the field named); or the"
            + " core refused the request (INVALID_RULE, UNKNOWN_SET, PATH_NOT_IN_SET, DUPLICATE_PICK); nothing kept",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "A path lies outside every root of the scan"
            + " (PATH_OUTSIDE_ROOTS, the field named); nothing kept",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_SCAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = NOT_COMPLETED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    ResponseEntity<PlanSummary> previewPlan(@PathVariable String id, @RequestBody PreviewPlanRequest request) {
        // Take the ID as a String: a malformed one must be the same 404 as an unknown one (checklist item 4).
        PlanStore store = store(id);
        PlanRequest core = PlanRequests.toCore(request, store::insideRoots);
        return switch (store.preview(core, preview)) {
            case Result.Success<KeptPlan> ok -> {
                PlanSummary summary = ok.value().summary();
                yield ResponseEntity.created(URI.create("/api/scans/" + id + "/plans/" + summary.planId()))
                        .body(summary);
            }
            case Result.Failure<KeptPlan> f ->
                    throw new ApiException(HttpStatus.BAD_REQUEST, f.code().name(), f.message());
        };
    }

    private PlanStore store(String id) {
        return switch (registry.completedState(id, PlanStore.class, PlanStore::new)) {
            case Result.Success<PlanStore> ok -> ok.value();
            case Result.Failure<PlanStore> f -> throw new ApiException(
                    f.code() == ErrorCode.UNKNOWN_SCAN ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                    f.code().name(), f.message());
        };
    }

    private KeptPlan plan(String id, String planId) {
        return store(id).find(planId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UNKNOWN_PLAN",
                "No plan " + planId + " was previewed for scan " + id + " in this server run"));
    }

    private static void checkPage(int page, int size) {
        if (page < 0 || size < 1 || size > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "page must be 0 or more and size 1 to 500");
        }
    }

    @GetMapping("/{id}/plans/{planId}")
    @Operation(operationId = "getPlan", summary = "The summary of a plan previewed for this scan in this server run")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PlanSummary.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_PLAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = NOT_COMPLETED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    PlanSummary getPlan(@PathVariable String id, @PathVariable String planId) {
        return plan(id, planId).summary();
    }

    @GetMapping("/{id}/plans/{planId}/operations")
    @Operation(operationId = "listPlanOperations", summary = "A page of a previewed plan's quarantine operations,"
            + " largest size first, each with its links, keeper and source")
    @ApiResponse(responseCode = "200", description = "The page; past the end it has no items and the same total",
            content = @Content(schema = @Schema(implementation = OperationPage.class)))
    @ApiResponse(responseCode = "400", description = BAD_PAGE,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_PLAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = NOT_COMPLETED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    OperationPage listPlanOperations(
            @PathVariable String id,
            @PathVariable String planId,
            @Parameter(description = "The page, from 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Operations per page, 1 to 500")
            @RequestParam(defaultValue = "50") int size) {
        KeptPlan kept = plan(id, planId);
        checkPage(page, size);
        return kept.operationPage(page, size);
    }

    @GetMapping("/{id}/plans/{planId}/conflicts")
    @Operation(operationId = "listPlanConflicts", summary = "A page of a previewed plan's conflicts, largest size"
            + " first, each with its set's copies, the rules that keep or would quarantine each, and its sources")
    @ApiResponse(responseCode = "200", description = "The page; past the end it has no items and the same total",
            content = @Content(schema = @Schema(implementation = ConflictPage.class)))
    @ApiResponse(responseCode = "400", description = BAD_PAGE,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = NO_SUCH_PLAN,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = NOT_COMPLETED,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    ConflictPage listPlanConflicts(
            @PathVariable String id,
            @PathVariable String planId,
            @Parameter(description = "The page, from 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Conflicts per page, 1 to 500")
            @RequestParam(defaultValue = "50") int size) {
        KeptPlan kept = plan(id, planId);
        checkPage(page, size);
        return kept.conflictPage(page, size);
    }
}
