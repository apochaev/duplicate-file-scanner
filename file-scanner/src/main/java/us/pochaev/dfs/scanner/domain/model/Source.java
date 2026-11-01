package us.pochaev.dfs.scanner.domain.model;

/**
 * What produced a plan entry or took part in a conflict (DFS-022 decision 5; {@code ui.md} U6).
 */
public sealed interface Source {

    /**
     * A rule of the request.
     *
     * @param index the rule's position in {@link PlanRequest#rules()}, from 0
     * @param rule  the rule itself
     */
    record FromRule(int index, KeepAndQuarantine rule) implements Source {
    }

    /**
     * A single-set pick of the request.
     *
     * @param pick the pick itself
     */
    record FromPick(KeepCopy pick) implements Source {
    }
}
