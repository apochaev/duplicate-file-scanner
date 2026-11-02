package us.pochaev.dfs.server.analysis;

import org.springframework.util.Assert;
import us.pochaev.dfs.server.scan.ScanRegistry;

/** Test-only: depends on Spring and on the server's scan package on purpose, so ArchitectureTest can show the DFS-046.AC-11 guard fires. */
class AnalysisGuardViolationFixture {

    Object registry(ScanRegistry registry) {
        Assert.notNull(registry, "registry");
        return registry;
    }
}
