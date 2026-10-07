package us.pochaev.dfs.scanner.domain.factory;

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
        throw new UnsupportedOperationException("not implemented: DFS-019");
    }
}
