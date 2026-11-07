package us.pochaev.dfs.server.plan;

import us.pochaev.dfs.scanner.domain.model.Conflict;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.LeftBehind;
import us.pochaev.dfs.scanner.domain.model.Operation;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.Source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Maps the core's {@code DeletionPlan} to the API's records (DFS-054 decisions 3, 4), so no core type reaches the spec.
 */
final class PlanViews {

    private PlanViews() {
    }

    /**
     * The plan as the server keeps it: summary ({@code planId} = {@code plan.id()}, totals, {@code leftBehind} with
     * {@code folder} as {@code Path.toString()}, the two counts), operations and conflicts sorted as {@link KeptPlan}
     * says. A {@code Source.FromRule} maps to {@code PlanSource("rule", index, null)}, a {@code Source.FromPick} to
     * {@code PlanSource("pick", null, digest)}. Each conflict is joined with the scan's set of its digest: its
     * {@code size}, and one {@link ConflictCopy} per physical copy (links sorted by path, copies by first link) whose
     * {@code quarantinedBy} and {@code keptBy} are computed from {@code request}'s rules and exceptions as
     * {@link ConflictCopy} documents, among the conflict's rule sources only.
     *
     * @param plan    the core's plan for {@code request} on {@code scan}
     * @param request the core request it was previewed from
     * @param scan    the scan's whole result (for each conflict's set)
     */
    static KeptPlan keptPlan(DeletionPlan plan, PlanRequest request, ScanResult scan) {
        List<PlanOperation> operations = plan.operations().stream()
                .map(op -> (Operation.Quarantine) op)
                .map(PlanViews::operation)
                .sorted(Comparator.comparingLong(PlanOperation::size).reversed()
                        .thenComparing(PlanOperation::digest)
                        .thenComparing(op -> op.links().getFirst()))
                .toList();
        List<PlanConflict> conflicts = plan.conflicts().stream()
                .map(c -> conflict(c, request, scan))
                .sorted(Comparator.comparingLong(PlanConflict::size).reversed().thenComparing(PlanConflict::digest))
                .toList();
        List<LeftBehindView> leftBehind = plan.leftBehind().stream().map(PlanViews::leftBehind).toList();
        PlanSummary summary = new PlanSummary(plan.id(),
                new PlanTotalsView(plan.totals().files(), plan.totals().bytes(), plan.totals().sets()),
                leftBehind, operations.size(), conflicts.size());
        return new KeptPlan(summary, operations, conflicts);
    }

    private static LeftBehindView leftBehind(LeftBehind l) {
        return new LeftBehindView(l.ruleIndex(), l.folder().toString(), l.files(), l.bytes());
    }

    private static PlanSource source(Source source) {
        return switch (source) {
            case Source.FromRule r -> new PlanSource("rule", r.index(), null);
            case Source.FromPick p -> new PlanSource("pick", null, p.pick().digest());
        };
    }

    private static PlanOperation operation(Operation.Quarantine op) {
        return new PlanOperation(op.digest(), op.size(), op.links().stream().map(Path::toString).toList(),
                op.keeper().toString(), source(op.source()));
    }

    private static List<List<Path>> copiesOf(DuplicateSet set) {
        List<List<Path>> copies = new ArrayList<>();
        for (PhysicalCopy copy : set.copies()) {
            copies.add(copy.links().stream().map(FileEntry::path).sorted().toList());
        }
        copies.sort(Comparator.comparing(List::getFirst));
        return copies;
    }

    private static PlanConflict conflict(Conflict conflict, PlanRequest request, ScanResult scan) {
        DuplicateSet set = scan.sets().stream().filter(s -> s.digest().equals(conflict.digest())).findFirst()
                .orElseThrow();
        List<List<Path>> copies = copiesOf(set);
        List<Integer> ruleIndexes = conflict.sources().stream()
                .filter(s -> s instanceof Source.FromRule)
                .map(s -> ((Source.FromRule) s).index())
                .sorted().toList();
        List<Integer> keeperOf = new ArrayList<>();
        for (int rule : ruleIndexes) {
            Path keep = request.rules().get(rule).keep();
            int keeper = -1;
            for (int c = 0; c < copies.size(); c++) {
                if (anyUnder(copies.get(c), keep)) {
                    keeper = c;
                    break;
                }
            }
            keeperOf.add(keeper);
        }
        List<ConflictCopy> views = new ArrayList<>();
        for (int c = 0; c < copies.size(); c++) {
            List<Path> copy = copies.get(c);
            List<Integer> quarantinedBy = new ArrayList<>();
            List<Integer> keptBy = new ArrayList<>();
            for (int k = 0; k < ruleIndexes.size(); k++) {
                KeepAndQuarantine rule = request.rules().get(ruleIndexes.get(k));
                if (allUnder(copy, rule.quarantineIn()) && !anyUnder(copy, rule.keep())
                        && request.exceptions().stream().noneMatch(e -> anyUnder(copy, e))) {
                    quarantinedBy.add(ruleIndexes.get(k));
                }
                if (keeperOf.get(k) == c) {
                    keptBy.add(ruleIndexes.get(k));
                }
            }
            views.add(new ConflictCopy(copy.stream().map(Path::toString).toList(), quarantinedBy, keptBy));
        }
        return new PlanConflict(conflict.digest(), set.size(), views,
                conflict.sources().stream().map(PlanViews::source).toList());
    }

    private static boolean anyUnder(List<Path> copy, Path folder) {
        return copy.stream().anyMatch(l -> l.startsWith(folder));
    }

    private static boolean allUnder(List<Path> copy, Path folder) {
        return copy.stream().allMatch(l -> l.startsWith(folder));
    }
}
