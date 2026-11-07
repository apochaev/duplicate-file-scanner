package us.pochaev.dfs.server.plan;

import us.pochaev.dfs.scanner.domain.model.PlanRequest;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Checks a preview request's paths and turns it into the core's {@code PlanRequest} (DFS-054 decision 5).
 * <p>
 * Stubs (design phase): every method throws.
 */
final class PlanRequests {

    private PlanRequests() {
    }

    /**
     * Checks every path in request order: each rule's {@code keep}, then its {@code quarantineIn}
     * ({@code rules[i].keep}, {@code rules[i].quarantineIn}), then each exception ({@code exceptions[i]}), then each
     * pick's {@code keep} ({@code picks[i].keep}). For the first bad one it throws, naming that field in the message:
     * {@code ApiException(400, "RELATIVE_PATH")} for a {@code null}, blank or relative path;
     * {@code ApiException(403, "PATH_OUTSIDE_ROOTS")} for an unparsable path or one that, normalized (so {@code ..} is
     * resolved), is not inside a root. A {@code null} list counts as empty; a {@code null} pick digest becomes
     * {@code ""}, which the core refuses as {@code UNKNOWN_SET}.
     *
     * @param body        the request as the client sent it
     * @param insideRoots whether an absolute, normalized path is inside one of the scan's roots
     *                    ({@link PlanStore#insideRoots})
     * @return the core request, every path normalized, lists in request order
     * @throws us.pochaev.dfs.server.api.ApiException for the first bad path; nothing else is checked here
     */
    static PlanRequest toCore(PreviewPlanRequest body, Predicate<Path> insideRoots) {
        throw new UnsupportedOperationException("not implemented: DFS-054");
    }
}
