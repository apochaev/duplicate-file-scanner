package us.pochaev.dfs.server.plan;

import org.springframework.http.HttpStatus;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.KeepCopy;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.server.api.ApiException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Checks a preview request's paths and turns it into the core's {@code PlanRequest} (DFS-054 decision 5).
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
        List<PlanRuleRequest> bodyRules = body.rules() == null ? List.of() : body.rules();
        List<KeepAndQuarantine> rules = new ArrayList<>();
        for (int i = 0; i < bodyRules.size(); i++) {
            PlanRuleRequest rule = bodyRules.get(i);
            Path keep = path(rule == null ? null : rule.keep(), "rules[" + i + "].keep", insideRoots);
            Path quarantineIn = path(rule.quarantineIn(), "rules[" + i + "].quarantineIn", insideRoots);
            rules.add(new KeepAndQuarantine(keep, quarantineIn));
        }
        List<String> bodyExceptions = body.exceptions() == null ? List.of() : body.exceptions();
        List<Path> exceptions = new ArrayList<>();
        for (int i = 0; i < bodyExceptions.size(); i++) {
            exceptions.add(path(bodyExceptions.get(i), "exceptions[" + i + "]", insideRoots));
        }
        List<PlanPickRequest> bodyPicks = body.picks() == null ? List.of() : body.picks();
        List<KeepCopy> picks = new ArrayList<>();
        for (int i = 0; i < bodyPicks.size(); i++) {
            PlanPickRequest pick = bodyPicks.get(i);
            Path keep = path(pick == null ? null : pick.keep(), "picks[" + i + "].keep", insideRoots);
            picks.add(new KeepCopy(pick.digest() == null ? "" : pick.digest(), keep));
        }
        return new PlanRequest(rules, exceptions, picks);
    }

    private static Path path(String value, String field, Predicate<Path> insideRoots) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RELATIVE_PATH", field + " is not an absolute path");
        }
        Path path;
        try {
            path = Path.of(value).normalize();
        } catch (InvalidPathException e) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PATH_OUTSIDE_ROOTS",
                    field + " lies outside every root of the scan: " + value);
        }
        if (!path.isAbsolute()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RELATIVE_PATH",
                    field + " is not an absolute path: " + value);
        }
        if (!insideRoots.test(path)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PATH_OUTSIDE_ROOTS",
                    field + " lies outside every root of the scan: " + value);
        }
        return path;
    }
}
