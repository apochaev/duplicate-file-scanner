package us.pochaev.dfs.scanner.domain.factory;

import us.pochaev.dfs.scanner.application.CheckPlanDriftService;
import us.pochaev.dfs.scanner.application.ListDirectoryService;
import us.pochaev.dfs.scanner.application.PlanExecutionService;
import us.pochaev.dfs.scanner.application.PlanVerifyService;
import us.pochaev.dfs.scanner.domain.model.ExecutionConfig;
import us.pochaev.dfs.scanner.infra.adapter.JsonLinesManifestWriter;
import us.pochaev.dfs.scanner.infra.adapter.NioQuarantineDeleter;
import us.pochaev.dfs.scanner.application.PreviewDeletionPlanService;
import us.pochaev.dfs.scanner.application.ScanPipeline;
import us.pochaev.dfs.scanner.application.ScanService;
import us.pochaev.dfs.scanner.application.VerifyPlanContentService;
import us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift;
import us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory;
import us.pochaev.dfs.scanner.domain.usecases.query.PreviewDeletionPlan;
import us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent;
import us.pochaev.dfs.scanner.infra.adapter.InMemoryJobStore;
import us.pochaev.dfs.scanner.infra.adapter.InMemoryScanRepository;
import us.pochaev.dfs.scanner.infra.adapter.NioContentComparator;
import us.pochaev.dfs.scanner.infra.adapter.NioContentHasher;
import us.pochaev.dfs.scanner.infra.adapter.NioDirectoryReader;
import us.pochaev.dfs.scanner.infra.adapter.NioFileAttributesReader;
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

    /**
     * Builds the plan drift check (DFS-033 decision 7) on the NIO attributes reader: stateless and synchronous, with
     * no scan state and no executor, so it is not part of {@link ScanUseCases}.
     *
     * @return a new {@link CheckPlanDrift}
     */
    public static CheckPlanDrift checkPlanDrift() {
        return new CheckPlanDriftService(new NioFileAttributesReader());
    }

    /**
     * Builds the plan content verify (DFS-034 decision 7) on the NIO attributes reader and the NIO content comparator
     * (1 MiB chunks): stateless, synchronous and read-only, with no scan state and no executor, so it is not part of
     * {@link ScanUseCases}.
     *
     * @return a new {@link VerifyPlanContent}
     */
    public static VerifyPlanContent verifyPlanContent() {
        return new VerifyPlanContentService(new NioFileAttributesReader(), new NioContentComparator());
    }

    /**
     * Builds a fresh, independent set of the plan verify job use cases (DFS-042 decision 6), all four backed by one
     * service and one in-memory job store, running DFS-034's verify on the NIO attributes reader and the NIO content
     * comparator (1 MiB chunks). Jobs run on {@code executor}, used as given: this method creates no thread or executor
     * of its own, so the executor's lifecycle stays with the caller.
     *
     * @param executor runs every job started through the returned {@link PlanVerifyUseCases#start}
     * @return a new set whose ports share job state with each other and with no other set
     * @throws NullPointerException if {@code executor} is null
     */
    public static PlanVerifyUseCases planVerifyUseCases(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        PlanVerifyService service = new PlanVerifyService(
                new VerifyPlanContentService(new NioFileAttributesReader(), new NioContentComparator()),
                new InMemoryJobStore<>(), executor);
        return new PlanVerifyUseCases(service, service, service, service);
    }

    /**
     * Builds a fresh, independent set of the plan execution use cases (DFS-051 decision 10), all four backed by one
     * service and one in-memory job store, on the NIO attributes reader, the NIO content comparator (1 MiB chunks), the
     * NIO quarantine deleter (with {@code config}'s quarantine anchors) and the JSON Lines manifest writer. Runs go on
     * {@code executor}, used as given. Nothing is created on disk until a run starts: the manifest folder and each
     * quarantine folder are created on first use (decision 13).
     *
     * @param executor runs every run started through the returned {@link PlanExecutionUseCases#start}
     * @param config   the manifest folder and quarantine anchors
     * @return a new set whose ports share job state with each other and with no other set
     * @throws NullPointerException if an argument is null
     */
    public static PlanExecutionUseCases planExecutionUseCases(Executor executor, ExecutionConfig config) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(config, "config");
        PlanExecutionService service = new PlanExecutionService(new NioFileAttributesReader(),
                new NioContentComparator(), new NioQuarantineDeleter(config.quarantineAnchors()),
                new JsonLinesManifestWriter(), new InMemoryJobStore<>(), executor, config);
        return new PlanExecutionUseCases(service, service, service, service);
    }
}
