package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.ExecutionConfig;
import us.pochaev.dfs.scanner.domain.model.ExecutionEvent;
import us.pochaev.dfs.scanner.domain.model.ExecutionListener;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.JobStatus;
import us.pochaev.dfs.scanner.domain.model.MovedFile;
import us.pochaev.dfs.scanner.domain.model.Operation;
import us.pochaev.dfs.scanner.domain.model.OperationOutcome;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionReport;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionSnapshot;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.SkipCause;
import us.pochaev.dfs.scanner.domain.model.SkippedFile;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelPlanExecution;
import us.pochaev.dfs.scanner.domain.usecases.command.StartPlanExecution;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanExecutionResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanExecutionStatus;
import us.pochaev.dfs.scanner.infra.port.ContentComparator;
import us.pochaev.dfs.scanner.infra.port.FileAttributesReader;
import us.pochaev.dfs.scanner.infra.port.FileDeleter;
import us.pochaev.dfs.scanner.infra.port.JobStore;
import us.pochaev.dfs.scanner.infra.port.ManifestLine;
import us.pochaev.dfs.scanner.infra.port.ManifestWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * Executes deletion plans into quarantine as background jobs on a caller-supplied {@link Executor} and tracks each by
 * {@link PlanExecutionId} (DFS-051). The lifecycle (QUEUED to a terminal status, cancel, rejection, failures) is the
 * shared {@link JobRunner}'s; this class adds the whole-plan validation on the caller's thread, the per-link
 * check-compare-move loop with its manifest lines, the snapshot kept current from the execution's events, the caller's
 * listener with its own detach, and the report. Stateless and shareable: its fields are its ports, the manifest
 * folder setting and the job runner (which holds the store and the executor).
 */
public final class PlanExecutionService implements StartPlanExecution, CancelPlanExecution, GetPlanExecutionStatus,
        GetPlanExecutionResult {

    private final FileAttributesReader reader;
    private final ContentComparator comparator;
    private final FileDeleter deleter;
    private final ManifestWriter manifests;
    private final ExecutionConfig config;
    private final JobRunner<PlanExecutionId, PlanExecutionProgress, PlanExecutionReport> jobs;

    /**
     * @param reader     reads each candidate's and keeper's attributes without following links (drift, decision 3)
     * @param comparator compares each operation's first checked link with its keeper (decision 4)
     * @param deleter    names quarantine paths and moves files (decision 5)
     * @param manifests  writes each run's manifest (decision 6)
     * @param store      holds every run's record
     * @param executor   runs every run's task
     * @param config     the manifest folder (its anchors are the deleter's business)
     * @throws NullPointerException if any argument is null
     */
    public PlanExecutionService(FileAttributesReader reader, ContentComparator comparator, FileDeleter deleter,
                                ManifestWriter manifests,
                                JobStore<PlanExecutionId, Job<PlanExecutionProgress, PlanExecutionReport>> store,
                                Executor executor, ExecutionConfig config) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.comparator = Objects.requireNonNull(comparator, "comparator");
        this.deleter = Objects.requireNonNull(deleter, "deleter");
        this.manifests = Objects.requireNonNull(manifests, "manifests");
        this.config = Objects.requireNonNull(config, "config");
        this.jobs = new JobRunner<>(store, executor, new ExecutionOutcomes());
    }

    @Override
    public Result<PlanExecutionId> start(ScanResult scan, DeletionPlan plan) {
        Objects.requireNonNull(scan, "scan");
        Objects.requireNonNull(plan, "plan");
        return begin(scan, plan, null);
    }

    @Override
    public Result<PlanExecutionId> start(ScanResult scan, DeletionPlan plan, ExecutionListener listener) {
        Objects.requireNonNull(scan, "scan");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(listener, "listener");
        return begin(scan, plan, listener);
    }

    @Override
    public Result<PlanExecutionSnapshot> cancel(PlanExecutionId id) {
        Objects.requireNonNull(id, "id");
        return jobs.cancel(id).<Result<PlanExecutionSnapshot>>map(j -> new Result.Success<>(snapshot(j)))
                .orElseGet(() -> unknown(id));
    }

    @Override
    public Result<PlanExecutionSnapshot> status(PlanExecutionId id) {
        Objects.requireNonNull(id, "id");
        return jobs.find(id).<Result<PlanExecutionSnapshot>>map(j -> new Result.Success<>(snapshot(j)))
                .orElseGet(() -> unknown(id));
    }

    @Override
    public Result<PlanExecutionReport> result(PlanExecutionId id) {
        Objects.requireNonNull(id, "id");
        Optional<Job<PlanExecutionProgress, PlanExecutionReport>> found = jobs.find(id);
        if (found.isEmpty()) {
            return unknown(id);
        }
        Job<PlanExecutionProgress, PlanExecutionReport> job = found.get();
        if (job.status() == JobStatus.COMPLETED || job.status() == JobStatus.CANCELLED) {
            return new Result.Success<>(job.result());
        }
        return new Result.Failure<>(ErrorCode.PLAN_EXECUTION_NOT_FINISHED,
                "plan execution " + id.value() + " has status " + job.status() + ", no result");
    }

    private static PlanExecutionSnapshot snapshot(Job<PlanExecutionProgress, PlanExecutionReport> job) {
        PlanExecutionProgress p = job.progress();
        return new PlanExecutionSnapshot(job.status(), p.plan().id(), p.operationsDone(),
                p.plan().operations().size(), p.bytesDone(), p.bytesTotal(), p.filesMoved(), p.bytesMoved(),
                job.failure());
    }

    private static <T> Result<T> unknown(PlanExecutionId id) {
        return new Result.Failure<>(ErrorCode.UNKNOWN_PLAN_EXECUTION, "unknown plan execution: " + id.value());
    }

    /** Run-local state shared by one run's work and settle hook; never a service field. */
    private static final class Run {
        ManifestWriter.Session session;
        int moved;
        int skipped;
        int notAttempted;
        boolean detached;
        String listenerFailure = "";
    }

    private Result<PlanExecutionId> begin(ScanResult scan, DeletionPlan plan, ExecutionListener listener) {
        String recomputed = PlanIds.of(plan.operations(), plan.conflicts());
        if (!recomputed.equals(plan.id())) {
            return new Result.Failure<>(ErrorCode.PLAN_EDITED,
                    "plan " + plan.id() + " does not match its content (" + recomputed + ")");
        }
        if (!plan.conflicts().isEmpty()) {
            return new Result.Failure<>(ErrorCode.PLAN_HAS_CONFLICTS, "plan has " + plan.conflicts().size()
                    + " conflicts, first in set " + plan.conflicts().get(0).digest());
        }
        List<Operation.Quarantine> operations = new ArrayList<>();
        List<Map<Path, FileEntry>> entriesPerOperation = new ArrayList<>();
        long bytesTotal = 0;
        int i = 0;
        for (Operation op : plan.operations()) {
            Operation.Quarantine q = (Operation.Quarantine) op;
            DuplicateSet set = null;
            for (DuplicateSet candidate : scan.sets()) {
                if (candidate.digest().equals(q.digest())) {
                    set = candidate;
                    break;
                }
            }
            if (set == null) {
                return new Result.Failure<>(ErrorCode.PLAN_NOT_FROM_SCAN,
                        "operation " + i + ": no set with digest " + q.digest() + " in the scan");
            }
            Map<Path, FileEntry> entries = new HashMap<>();
            for (FileEntry file : set.files()) {
                entries.put(file.path(), file);
            }
            for (Path link : q.links()) {
                if (!entries.containsKey(link)) {
                    return new Result.Failure<>(ErrorCode.PLAN_NOT_FROM_SCAN, "operation " + i + ": " + link
                            + " is not a copy of set " + q.digest() + " in the scan");
                }
            }
            Set<Path> wanted = new HashSet<>(q.links());
            PhysicalCopy own = null;
            for (PhysicalCopy copy : set.copies()) {
                Set<Path> paths = new HashSet<>();
                copy.links().forEach(l -> paths.add(l.path()));
                if (paths.equals(wanted)) {
                    own = copy;
                    break;
                }
            }
            String prefix = "operation " + i + " (set " + q.digest() + "): keeper " + q.keeper()
                    + " is not a link of another physical copy";
            if (own == null) {
                return new Result.Failure<>(ErrorCode.KEEPER_NOT_SEPARATE_COPY,
                        prefix + " (links are not one physical copy)");
            }
            boolean separate = false;
            for (PhysicalCopy copy : set.copies()) {
                if (copy != own && copy.links().stream().anyMatch(l -> l.path().equals(q.keeper()))) {
                    separate = true;
                    break;
                }
            }
            if (!separate) {
                return new Result.Failure<>(ErrorCode.KEEPER_NOT_SEPARATE_COPY, prefix);
            }
            operations.add(q);
            entriesPerOperation.add(entries);
            bytesTotal += q.size();
            i++;
        }
        PlanExecutionId runId = PlanExecutionId.random();
        Path manifest;
        try {
            manifest = manifests.create(config.manifestFolder(),
                    new ManifestLine.Run(runId, plan.id(), scan.roots(), Instant.now()));
        } catch (IOException e) {
            return new Result.Failure<>(ErrorCode.MANIFEST_UNWRITABLE,
                    "manifest folder " + config.manifestFolder() + ": " + describe(e));
        }
        final long grand = bytesTotal;
        PlanExecutionProgress initial = new PlanExecutionProgress(runId, manifest, plan, 0, 0, grand, 0, 0);
        Run run = new Run();
        return jobs.submit(runId, initial,
                (progress, stop) -> execute(runId, manifest, plan, operations, entriesPerOperation, grand,
                        listener, run, progress, stop),
                job -> settle(job, run));
    }

    private void settle(Job<PlanExecutionProgress, PlanExecutionReport> job, Run run) {
        ManifestWriter.Session session = run.session;
        if (session == null) {
            return;
        }
        try {
            session.append(new ManifestLine.End(job.status(), run.moved, run.skipped, run.notAttempted,
                    Instant.now()));
        } catch (IOException | RuntimeException e) {
            // one attempt: the status is already recorded and cannot change
        } finally {
            try {
                session.close();
            } catch (IOException | RuntimeException e) {
                // nothing more to do
            }
        }
    }

    private static void append(ManifestWriter.Session session, ManifestLine line) {
        try {
            session.append(line);
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    private PlanExecutionReport execute(PlanExecutionId runId, Path manifest, DeletionPlan plan,
                                        List<Operation.Quarantine> operations,
                                        List<Map<Path, FileEntry>> entriesPerOperation, long grand,
                                        ExecutionListener listener, Run run,
                                        JobRunner.Progress<PlanExecutionProgress> progress, BooleanSupplier stop) {
        ManifestWriter.Session session;
        try {
            session = manifests.open(manifest);
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
        run.session = session;
        ExecutionListener combined = event -> {
            progress.update(p -> switch (event) {
                case ExecutionEvent.BytesCompared b -> new PlanExecutionProgress(p.runId(), p.manifest(), p.plan(),
                        p.operationsDone(), b.bytesDone(), p.bytesTotal(), p.filesMoved(), p.bytesMoved());
                case ExecutionEvent.OperationDone d -> new PlanExecutionProgress(p.runId(), p.manifest(), p.plan(),
                        d.done(), p.bytesDone(), p.bytesTotal(), p.filesMoved() + movedCount(d.outcome()),
                        p.bytesMoved() + (d.outcome() instanceof OperationOutcome.Moved ? d.outcome().size() : 0));
            });
            if (listener != null && !run.detached) {
                try {
                    listener.onEvent(event);
                } catch (RuntimeException e) {
                    run.detached = true;
                    String m = e.getMessage();
                    run.listenerFailure = m == null || m.isBlank() ? e.toString() : m;
                }
            }
        };
        int total = operations.size();
        List<OperationOutcome> outcomes = new ArrayList<>();
        boolean cancelled = false;
        long before = 0;
        long[] last = {0};
        for (int i = 0; i < total; i++) {
            if (stop.getAsBoolean()) {
                cancelled = true;
                for (int j = i; j < total; j++) {
                    Operation.Quarantine rest = operations.get(j);
                    outcomes.add(new OperationOutcome.NotAttempted(rest.digest(), rest.size(), rest.keeper(),
                            rest.links()));
                    run.notAttempted += rest.links().size();
                }
                break;
            }
            Operation.Quarantine q = operations.get(i);
            Map<Path, FileEntry> entries = entriesPerOperation.get(i);
            final long base = before;
            List<MovedFile> moved = new ArrayList<>();
            List<SkippedFile> skipped = new ArrayList<>();
            boolean compareDone = false;
            SkippedFile compareSkip = null;
            for (Path link : q.links()) {
                Path target;
                try {
                    target = deleter.quarantinePath(link, runId);
                } catch (IOException e) {
                    SkippedFile s = new SkippedFile(link, SkipCause.QUARANTINE_NOT_WRITABLE, link, describe(e));
                    append(session, new ManifestLine.Skipped(s.path(), s.cause(), s.subject(), s.detail()));
                    skipped.add(s);
                    run.skipped++;
                    continue;
                }
                append(session, new ManifestLine.Moving(link, target, q.keeper(), q.digest(), q.size(),
                        entries.get(link).lastModified()));
                SkippedFile skip = drift(link, link, entries.get(link));
                if (skip == null) {
                    skip = drift(link, q.keeper(), entries.get(q.keeper()));
                }
                if (skip == null && !compareDone) {
                    compareDone = true;
                    try {
                        ContentComparator.Comparison c = comparator.compare(link, q.keeper(), n -> {
                            long done = base + Math.min(n, q.size());
                            last[0] = done;
                            combined.onEvent(new ExecutionEvent.BytesCompared(done, grand));
                        }, () -> false);
                        if (c instanceof ContentComparator.Comparison.Differs d) {
                            compareSkip = new SkippedFile(link, SkipCause.DIFFERS, link,
                                    "first difference at byte " + d.offset());
                        }
                    } catch (IOException e) {
                        compareSkip = new SkippedFile(link, SkipCause.UNREADABLE, link, describe(e));
                    }
                }
                if (skip == null && compareSkip != null) {
                    skip = new SkippedFile(link, compareSkip.cause(), link, compareSkip.detail());
                }
                if (skip == null) {
                    switch (deleter.move(link, target)) {
                        case FileDeleter.MoveResult.Moved m -> moved.add(new MovedFile(link, target));
                        case FileDeleter.MoveResult.QuarantineNotWritable n ->
                                skip = new SkippedFile(link, SkipCause.QUARANTINE_NOT_WRITABLE, link, n.detail());
                        case FileDeleter.MoveResult.Failed f ->
                                skip = new SkippedFile(link, SkipCause.MOVE_FAILED, link, f.detail());
                    }
                }
                if (skip == null) {
                    append(session, new ManifestLine.Moved(link, target));
                    run.moved++;
                } else {
                    append(session, new ManifestLine.Skipped(skip.path(), skip.cause(), skip.subject(),
                            skip.detail()));
                    skipped.add(skip);
                    run.skipped++;
                }
            }
            OperationOutcome outcome;
            if (skipped.isEmpty()) {
                outcome = new OperationOutcome.Moved(q.digest(), q.size(), q.keeper(), moved);
            } else if (moved.isEmpty()) {
                outcome = new OperationOutcome.Skipped(q.digest(), q.size(), q.keeper(), skipped);
            } else {
                outcome = new OperationOutcome.PartlyMoved(q.digest(), q.size(), q.keeper(), moved, skipped);
            }
            before += q.size();
            if (last[0] != before) {
                last[0] = before;
                combined.onEvent(new ExecutionEvent.BytesCompared(before, grand));
            }
            outcomes.add(outcome);
            combined.onEvent(new ExecutionEvent.OperationDone(outcome, i + 1, total));
        }
        return report(runId, plan.id(), manifest, outcomes, cancelled, run.listenerFailure);
    }

    private static int movedCount(OperationOutcome outcome) {
        return switch (outcome) {
            case OperationOutcome.Moved m -> m.moved().size();
            case OperationOutcome.PartlyMoved m -> m.moved().size();
            case OperationOutcome.Skipped s -> 0;
            case OperationOutcome.NotAttempted n -> 0;
        };
    }

    private static PlanExecutionReport report(PlanExecutionId runId, String planId, Path manifest,
                                              List<OperationOutcome> outcomes, boolean cancelled,
                                              String listenerFailure) {
        int filesMoved = 0;
        long bytesMoved = 0;
        int filesSkipped = 0;
        long bytesSkipped = 0;
        for (OperationOutcome o : outcomes) {
            switch (o) {
                case OperationOutcome.Moved m -> {
                    filesMoved += m.moved().size();
                    bytesMoved += m.size();
                }
                case OperationOutcome.PartlyMoved m -> {
                    filesMoved += m.moved().size();
                    filesSkipped += m.skipped().size();
                    bytesSkipped += m.size();
                }
                case OperationOutcome.Skipped s -> {
                    filesSkipped += s.skipped().size();
                    bytesSkipped += s.size();
                }
                case OperationOutcome.NotAttempted n -> {
                }
            }
        }
        return new PlanExecutionReport(runId, planId, manifest, outcomes, cancelled, filesMoved, bytesMoved,
                filesSkipped, bytesSkipped, listenerFailure);
    }

    /**
     * Reads {@code subject} without following links and compares it with the scan's entry; null when unchanged.
     * The skip names {@code path} as the skipped link and {@code subject} as the path that drifted.
     */
    private SkippedFile drift(Path path, Path subject, FileEntry recorded) {
        try {
            BasicFileAttributes a = reader.read(subject);
            if (!(a.isRegularFile() && !a.isOther() && !a.isSymbolicLink())) {
                return new SkippedFile(path, SkipCause.NOT_A_REGULAR_FILE, subject, "is " + what(a));
            }
            if (a.size() != recorded.size()) {
                return new SkippedFile(path, SkipCause.SIZE_CHANGED, subject,
                        "size was " + recorded.size() + ", now " + a.size());
            }
            if (!a.lastModifiedTime().toInstant().equals(recorded.lastModified())) {
                return new SkippedFile(path, SkipCause.MODIFIED_CHANGED, subject, "last-modified time changed");
            }
            return null;
        } catch (NoSuchFileException e) {
            return new SkippedFile(path, SkipCause.MISSING, subject, "no longer exists");
        } catch (IOException e) {
            return new SkippedFile(path, SkipCause.UNREADABLE, subject, describe(e));
        }
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

    private static String describe(IOException e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.toString() : m;
    }

    /** An execution's two use-case-specific results (the story's {@code design.md}, "Behavior"). */
    private static final class ExecutionOutcomes
            implements JobRunner.Outcomes<PlanExecutionProgress, PlanExecutionReport> {

        /** Every operation {@code NotAttempted}, {@code cancelled} true, all totals 0, no listener failure. */
        @Override
        public PlanExecutionReport cancelledBeforeStart(PlanExecutionProgress progress) {
            List<OperationOutcome> outcomes = new ArrayList<>();
            for (Operation op : progress.plan().operations()) {
                Operation.Quarantine q = (Operation.Quarantine) op;
                outcomes.add(new OperationOutcome.NotAttempted(q.digest(), q.size(), q.keeper(), q.links()));
            }
            return new PlanExecutionReport(progress.runId(), progress.plan().id(), progress.manifest(), outcomes,
                    true, 0, 0, 0, 0, "");
        }

        /** The same report with {@code cancelled} true; outcomes, totals and listenerFailure unchanged. */
        @Override
        public PlanExecutionReport markCancelled(PlanExecutionReport r) {
            return new PlanExecutionReport(r.runId(), r.planId(), r.manifest(), r.outcomes(), true, r.filesMoved(),
                    r.bytesMoved(), r.filesSkipped(), r.bytesSkipped(), r.listenerFailure());
        }
    }
}
