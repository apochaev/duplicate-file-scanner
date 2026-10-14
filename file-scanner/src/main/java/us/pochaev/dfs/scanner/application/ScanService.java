package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelScan;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;
import us.pochaev.dfs.scanner.infra.port.LocationResolver;
import us.pochaev.dfs.scanner.infra.port.ScanRepository;

import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;
import us.pochaev.dfs.scanner.infra.port.ScanRecord;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Runs scans on a caller-supplied {@link Executor} and tracks each by {@link ScanId} (DFS-018).
 * <p>
 * Stateless and shareable: its fields are its ports, its {@link ScanRunner} seam and the executor,
 * nothing else (decision 8). Every piece of per-scan state is one {@code ScanRecord} behind
 * {@link ScanRepository}, and every status transition is one {@link ScanRepository#update} that
 * checks the current status first. See the story's {@code design.md} for the transition table.
 */
public final class ScanService implements StartScan, CancelScan, GetScanStatus, GetScanResult {

    private final LocationResolver resolver;
    private final ScanRunner runner;
    private final ScanRepository repository;
    private final Executor executor;

    /**
     * The production constructor: runs scans through the real {@code pipeline}.
     *
     * @throws NullPointerException if any argument is null
     */
    public ScanService(LocationResolver resolver, ScanPipeline pipeline, ScanRepository repository,
                       Executor executor) {
        this(resolver, (ScanRunner) Objects.requireNonNull(pipeline, "pipeline"), repository, executor);
    }

    /**
     * The seam constructor for {@code application} tests (decision 12).
     *
     * @throws NullPointerException if any argument is null
     */
    ScanService(LocationResolver resolver, ScanRunner runner, ScanRepository repository,
                Executor executor) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public Result<ScanId> start(List<Path> locations, ScanConfig config) {
        return begin(locations, config, null);
    }

    @Override
    public Result<ScanId> start(List<Path> locations, ScanConfig config, ScanListener listener) {
        Objects.requireNonNull(locations, "locations");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(listener, "listener");
        return begin(locations, config, listener);
    }

    private Result<ScanId> begin(List<Path> locations, ScanConfig config, ScanListener listener) {
        Objects.requireNonNull(locations, "locations");
        Objects.requireNonNull(config, "config");
        for (Path location : locations) {
            Objects.requireNonNull(location, "location");
        }
        if (locations.isEmpty()) {
            return new Result.Failure<>(ErrorCode.NO_LOCATIONS, "no locations to scan");
        }
        for (Path location : locations) {
            try {
                resolver.realPath(location);
            } catch (NoSuchFileException e) {
                return new Result.Failure<>(ErrorCode.LOCATION_NOT_FOUND, "location not found: " + location);
            } catch (NotDirectoryException e) {
                return new Result.Failure<>(ErrorCode.NOT_A_DIRECTORY, "not a directory: " + location);
            } catch (IOException e) {
                return new Result.Failure<>(ErrorCode.LOCATION_UNREADABLE,
                        "location unreadable: " + describe(e));
            }
        }
        List<Path> copy = List.copyOf(locations);
        ScanId id = ScanId.random();
        repository.create(new ScanRecord(id,
                new ScanSnapshot(ScanStatus.QUEUED, null, 0, 0, 0, null, null), null));
        try {
            executor.execute(() -> runTask(id, copy, config, listener));
        } catch (RejectedExecutionException e) {
            markQueuedFailed(id, e);
            return new Result.Failure<>(ErrorCode.EXECUTOR_REJECTED,
                    "executor rejected the scan: " + describe(e));
        } catch (RuntimeException e) {
            markQueuedFailed(id, e);
            throw e;
        }
        return new Result.Success<>(id);
    }

    @Override
    public Result<ScanSnapshot> cancel(ScanId scanId) {
        Objects.requireNonNull(scanId, "scanId");
        return repository.update(scanId, r -> switch (r.snapshot().status()) {
                    case QUEUED -> withStatus(r, ScanStatus.CANCELLED);
                    case RUNNING -> withStatus(r, ScanStatus.CANCELLING);
                    default -> r;
                })
                .<Result<ScanSnapshot>>map(r -> new Result.Success<>(r.snapshot()))
                .orElseGet(() -> unknown(scanId));
    }

    @Override
    public Result<ScanSnapshot> status(ScanId scanId) {
        Objects.requireNonNull(scanId, "scanId");
        return repository.find(scanId)
                .<Result<ScanSnapshot>>map(r -> new Result.Success<>(r.snapshot()))
                .orElseGet(() -> unknown(scanId));
    }

    @Override
    public Result<ScanResult> result(ScanId scanId) {
        Objects.requireNonNull(scanId, "scanId");
        Optional<ScanRecord> found = repository.find(scanId);
        if (found.isEmpty()) {
            return unknown(scanId);
        }
        ScanRecord r = found.get();
        if (r.snapshot().status() == ScanStatus.COMPLETED && r.result() != null) {
            return new Result.Success<>(r.result());
        }
        return new Result.Failure<>(ErrorCode.SCAN_NOT_COMPLETED,
                "scan " + scanId.value() + " has status " + r.snapshot().status() + ", no result");
    }

    // ---- the task, on the executor's thread ----

    private void runTask(ScanId id, List<Path> locations, ScanConfig config, ScanListener callerListener) {
        Optional<ScanRecord> picked = repository.update(id, r ->
                r.snapshot().status() == ScanStatus.QUEUED ? withStatus(r, ScanStatus.RUNNING) : r);
        if (picked.isEmpty() || picked.get().snapshot().status() != ScanStatus.RUNNING) {
            return;
        }
        boolean[] detached = {false};
        ScanListener listener = event -> {
            repository.update(id, r -> applyEvent(r, event));
            if (callerListener != null && !detached[0]) {
                try {
                    callerListener.onEvent(event);
                } catch (RuntimeException e) {
                    detached[0] = true;
                    String text = describe(e);
                    repository.update(id, r -> r.snapshot().listenerFailure() != null ? r
                            : replace(r, s -> new ScanSnapshot(s.status(), s.phase(), s.filesWalked(),
                                    s.groupsDone(), s.groupsTotal(), s.failureMessage(), text)));
                }
            }
        };
        BooleanSupplier stop = () -> repository.find(id)
                .map(r -> r.snapshot().status() != ScanStatus.RUNNING).orElse(true);
        try {
            ScanResult result = runner.run(locations, config, listener, stop);
            finish(id, ScanStatus.COMPLETED, result, null);
        } catch (CancellationException e) {
            finish(id, ScanStatus.CANCELLED, null, null);
        } catch (IOException | RuntimeException e) {
            finish(id, ScanStatus.FAILED, null, describe(e));
        } catch (Error e) {
            finish(id, ScanStatus.FAILED, null, describe(e));
            throw e;
        }
    }

    private void finish(ScanId id, ScanStatus status, ScanResult result, String failureMessage) {
        repository.update(id, r -> {
            ScanStatus current = r.snapshot().status();
            if (current != ScanStatus.RUNNING && current != ScanStatus.CANCELLING) {
                return r;
            }
            ScanSnapshot s = r.snapshot();
            return new ScanRecord(r.id(), new ScanSnapshot(status, s.phase(), s.filesWalked(),
                    s.groupsDone(), s.groupsTotal(), failureMessage, s.listenerFailure()), result);
        });
    }

    private void markQueuedFailed(ScanId id, RuntimeException e) {
        String text = describe(e);
        repository.update(id, r -> r.snapshot().status() != ScanStatus.QUEUED ? r
                : replace(r, s -> new ScanSnapshot(ScanStatus.FAILED, s.phase(), s.filesWalked(),
                        s.groupsDone(), s.groupsTotal(), text, s.listenerFailure())));
    }

    private static ScanRecord applyEvent(ScanRecord r, ScanEvent event) {
        ScanStatus status = r.snapshot().status();
        if (status != ScanStatus.RUNNING && status != ScanStatus.CANCELLING) {
            return r;
        }
        return replace(r, s -> switch (event) {
            case ScanEvent.PhaseStarted e -> new ScanSnapshot(s.status(), e.phase(), s.filesWalked(), 0, 0,
                    s.failureMessage(), s.listenerFailure());
            case ScanEvent.FileWalked e -> new ScanSnapshot(s.status(), s.phase(), e.filesSoFar(),
                    s.groupsDone(), s.groupsTotal(), s.failureMessage(), s.listenerFailure());
            case ScanEvent.GroupDone e -> new ScanSnapshot(s.status(), s.phase(), s.filesWalked(),
                    e.done(), e.total(), s.failureMessage(), s.listenerFailure());
            case ScanEvent.Skipped e -> s;
            // DFS-024 decision 6: the snapshot keeps its fields; these reach the caller's listener only
            case ScanEvent.DirectoryWalked e -> s;
            case ScanEvent.HashProgress e -> s;
        });
    }

    private static ScanRecord withStatus(ScanRecord r, ScanStatus status) {
        return replace(r, s -> new ScanSnapshot(status, s.phase(), s.filesWalked(), s.groupsDone(),
                s.groupsTotal(), s.failureMessage(), s.listenerFailure()));
    }

    private static ScanRecord replace(ScanRecord r, UnaryOperator<ScanSnapshot> change) {
        return new ScanRecord(r.id(), change.apply(r.snapshot()), r.result());
    }

    private static String describe(Throwable e) {
        return e.getClass().getName() + ": " + e.getMessage();
    }

    private static <T> Result<T> unknown(ScanId id) {
        return new Result.Failure<>(ErrorCode.UNKNOWN_SCAN, "unknown scan: " + id.value());
    }
}
