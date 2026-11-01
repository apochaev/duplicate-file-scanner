package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;
import java.util.List;

/**
 * What a user asks a deletion plan to do (DFS-022 decision 2): rules applied in order, global exceptions that no rule
 * quarantines at or under, and single-set picks that override the rules for their set.
 *
 * @param rules      the rules, in request order (an entry's {@link Source.FromRule#index()} is its position here)
 * @param exceptions files or folders no rule quarantines a copy at or under; never validated
 * @param picks      at most one pick per digest
 */
public record PlanRequest(List<KeepAndQuarantine> rules, List<Path> exceptions, List<KeepCopy> picks) {
}
