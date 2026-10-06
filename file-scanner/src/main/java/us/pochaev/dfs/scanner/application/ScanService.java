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

import java.nio.file.Path;
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
        throw new UnsupportedOperationException("not implemented: DFS-018");
    }

    /**
     * The seam constructor for {@code application} tests (decision 12).
     *
     * @throws NullPointerException if any argument is null
     */
    ScanService(LocationResolver resolver, ScanRunner runner, ScanRepository repository,
                Executor executor) {
        throw new UnsupportedOperationException("not implemented: DFS-018");
    }

    @Override
    public Result<ScanId> start(List<Path> locations, ScanConfig config) {
        throw new UnsupportedOperationException("not implemented: DFS-018");
    }

    @Override
    public Result<ScanId> start(List<Path> locations, ScanConfig config, ScanListener listener) {
        throw new UnsupportedOperationException("not implemented: DFS-018");
    }

    @Override
    public Result<ScanSnapshot> cancel(ScanId scanId) {
        throw new UnsupportedOperationException("not implemented: DFS-018");
    }

    @Override
    public Result<ScanSnapshot> status(ScanId scanId) {
        throw new UnsupportedOperationException("not implemented: DFS-018");
    }

    @Override
    public Result<ScanResult> result(ScanId scanId) {
        throw new UnsupportedOperationException("not implemented: DFS-018");
    }
}
