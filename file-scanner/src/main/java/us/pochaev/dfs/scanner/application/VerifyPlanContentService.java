package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.Operation;
import us.pochaev.dfs.scanner.domain.model.OperationCheck;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.VerifyEvent;
import us.pochaev.dfs.scanner.domain.model.VerifyListener;
import us.pochaev.dfs.scanner.domain.model.VerifyReport;
import us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent;
import us.pochaev.dfs.scanner.infra.port.ContentComparator;
import us.pochaev.dfs.scanner.infra.port.FileAttributesReader;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Verifies a previewed plan's content against its keepers (DFS-034) through a {@link FileAttributesReader} and a
 * {@link ContentComparator}: for each operation, both paths' attributes without following links, then a chunked
 * compare only for two regular files of the same size. Its only fields are the ports; the detached-listener state
 * and the byte counters are local to each call, so concurrent calls share nothing.
 */
public final class VerifyPlanContentService implements VerifyPlanContent {

    private final FileAttributesReader reader;
    private final ContentComparator comparator;

    public VerifyPlanContentService(FileAttributesReader reader, ContentComparator comparator) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.comparator = Objects.requireNonNull(comparator, "comparator");
    }

    @Override
    public Result<VerifyReport> verify(DeletionPlan plan, VerifyListener listener, BooleanSupplier cancelled) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(cancelled, "cancelled");
        Emitter out = new Emitter(listener);
        long bytesTotal = 0;
        for (Operation op : plan.operations()) {
            bytesTotal += ((Operation.Quarantine) op).size();
        }
        final long grand = bytesTotal;
        int total = plan.operations().size();
        List<OperationCheck> checks = new ArrayList<>();
        long before = 0;
        long[] last = {-1};
        int i = 0;
        for (Operation op : plan.operations()) {
            Operation.Quarantine q = (Operation.Quarantine) op;
            if (cancelled.getAsBoolean()) {
                return report(plan, checks, true, out);
            }
            final long base = before;
            OperationCheck check = check(q, q.links().get(0), q.keeper(), n -> {
                long done = base + Math.min(n, q.size());
                last[0] = done;
                out.emit(new VerifyEvent.BytesCompared(done, grand));
            }, cancelled);
            if (check == null) {
                return report(plan, checks, true, out);
            }
            checks.add(check);
            before += q.size();
            if (last[0] != before) {
                out.emit(new VerifyEvent.BytesCompared(before, grand));
                last[0] = before;
            }
            i++;
            out.emit(new VerifyEvent.EntryChecked(check, i, total));
        }
        return report(plan, checks, false, out);
    }

    private static Result<VerifyReport> report(DeletionPlan plan, List<OperationCheck> checks, boolean cancelled,
                                               Emitter out) {
        return new Result.Success<>(new VerifyReport(plan.id(), checks, cancelled, out.failure));
    }

    /** Returns null when the compare was cancelled. */
    private OperationCheck check(Operation.Quarantine q, Path candidate, Path keeper, LongConsumer progress,
                                 BooleanSupplier cancelled) {
        BasicFileAttributes ca = null;
        BasicFileAttributes ka = null;
        IOException cErr = null;
        IOException kErr = null;
        boolean cMissing = false;
        boolean kMissing = false;
        try {
            ca = reader.read(candidate);
        } catch (NoSuchFileException e) {
            cMissing = true;
        } catch (IOException e) {
            cErr = e;
        }
        try {
            ka = reader.read(keeper);
        } catch (NoSuchFileException e) {
            kMissing = true;
        } catch (IOException e) {
            kErr = e;
        }
        if (cMissing) {
            return of(q, candidate, keeper, OperationCheck.Outcome.CANDIDATE_MISSING, "");
        }
        if (kMissing) {
            return of(q, candidate, keeper, OperationCheck.Outcome.KEEPER_MISSING, "");
        }
        if (cErr == null && !regular(ca)) {
            return of(q, candidate, keeper, OperationCheck.Outcome.NOT_A_REGULAR_FILE, "candidate is " + what(ca));
        }
        if (kErr == null && !regular(ka)) {
            return of(q, candidate, keeper, OperationCheck.Outcome.NOT_A_REGULAR_FILE, "keeper is " + what(ka));
        }
        if (cErr != null) {
            return of(q, candidate, keeper, OperationCheck.Outcome.UNREADABLE, describe(cErr));
        }
        if (kErr != null) {
            return of(q, candidate, keeper, OperationCheck.Outcome.UNREADABLE, describe(kErr));
        }
        if (ca.size() != ka.size()) {
            return of(q, candidate, keeper, OperationCheck.Outcome.DIFFERS,
                    "sizes differ: " + ca.size() + " vs " + ka.size());
        }
        try {
            ContentComparator.Comparison c = comparator.compare(candidate, keeper, progress, cancelled);
            return switch (c) {
                case ContentComparator.Comparison.Identical x ->
                        of(q, candidate, keeper, OperationCheck.Outcome.IDENTICAL, "");
                case ContentComparator.Comparison.Differs d ->
                        of(q, candidate, keeper, OperationCheck.Outcome.DIFFERS,
                                "first difference at byte " + d.offset());
                case ContentComparator.Comparison.Cancelled x -> null;
            };
        } catch (IOException e) {
            return of(q, candidate, keeper, OperationCheck.Outcome.UNREADABLE, describe(e));
        }
    }

    private static OperationCheck of(Operation.Quarantine q, Path c, Path k, OperationCheck.Outcome o, String d) {
        return new OperationCheck(q.digest(), c, k, o, d);
    }

    private static boolean regular(BasicFileAttributes a) {
        return a.isRegularFile() && !a.isOther() && !a.isSymbolicLink();
    }

    private static String what(BasicFileAttributes a) {
        if (a.isSymbolicLink()) {
            return "a symlink";
        }
        if (a.isDirectory() && a.isOther()) {
            return "a junction";
        }
        if (a.isDirectory()) {
            return "a directory";
        }
        return "a special file";
    }

    private static String describe(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.toString() : m;
    }

    /** Wraps the listener; detached at its first throw. Local to one call. */
    private static final class Emitter {
        private final VerifyListener listener;
        private boolean detached;
        String failure = "";

        Emitter(VerifyListener listener) {
            this.listener = listener;
        }

        void emit(VerifyEvent event) {
            if (detached) {
                return;
            }
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                detached = true;
                failure = describe(e);
            }
        }
    }
}
