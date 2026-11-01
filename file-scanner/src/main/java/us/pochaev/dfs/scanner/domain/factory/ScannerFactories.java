package us.pochaev.dfs.scanner.domain.factory;

import us.pochaev.dfs.scanner.application.ListDirectoryService;
import us.pochaev.dfs.scanner.application.PreviewDeletionPlanService;
import us.pochaev.dfs.scanner.application.ScanPipeline;
import us.pochaev.dfs.scanner.application.ScanService;
import us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory;
import us.pochaev.dfs.scanner.domain.usecases.query.PreviewDeletionPlan;
import us.pochaev.dfs.scanner.infra.adapter.InMemoryScanRepository;
import us.pochaev.dfs.scanner.infra.adapter.NioContentHasher;
import us.pochaev.dfs.scanner.infra.adapter.NioDirectoryReader;
import us.pochaev.dfs.scanner.infra.adapter.NioFileIdentity;
import us.pochaev.dfs.scanner.infra.adapter.NioFileTreeWalker;
import us.pochaev.dfs.scanner.infra.adapter.NioLocationResolver;
import us.pochaev.dfs.scanner.infra.port.LocationResolver;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Entry point for building use-case implementations (CLAUDE.md design decision 5): plain static
 * factories that instantiate {@code application} and {@code infra.adapter} classes directly and
 * hand them out as port interfaces only. Holds no state; every call builds a fresh set.
 */
public final class ScannerFactories {

    private ScannerFactories() {
    }

    /**
     * Builds a fresh, independent set of the scan use cases, all four backed by one scan service and
     * one in-memory scan repository, wired to the default file-system adapters (DFS-019 decisions 2,
     * 3). Scans run on {@code executor}, which is used as given: this method creates no thread or
     * executor of its own, so the executor's lifecycle stays with the caller (decision 4).
     *
     * @param executor runs every scan started through the returned {@link ScanUseCases#start}
     * @return a new set whose ports share scan state with each other and with no other set
     * @throws NullPointerException if {@code executor} is null
     */
    public static ScanUseCases scanUseCases(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        LocationResolver resolver = new NioLocationResolver();
        ScanPipeline pipeline = new ScanPipeline(resolver, new NioFileTreeWalker(), new NioContentHasher(),
                new NioFileIdentity());
        ScanService service = new ScanService(resolver, pipeline, new InMemoryScanRepository(), executor);
        return new ScanUseCases(service, service, service, service);
    }

    /**
     * Builds the deletion-plan preview (DFS-022 decision 8): a pure function of a {@code ScanResult} and a request,
     * with no scan state and no executor, so it is not part of {@link ScanUseCases}.
     *
     * @return a new {@link PreviewDeletionPlan}
     */
    public static PreviewDeletionPlan previewDeletionPlan() {
        return new PreviewDeletionPlanService();
    }

    /**
     * Builds the one-level folder listing (DFS-032 decision 6) on the NIO directory reader: stateless, with no scan
     * state and no executor, so it is not part of {@link ScanUseCases}. It lists any folder it is given; limiting
     * paths to a scan's roots is the caller's job.
     *
     * @return a new {@link ListDirectory}
     */
    public static ListDirectory listDirectory() {
        return new ListDirectoryService(new NioDirectoryReader());
    }
}
