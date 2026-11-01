package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DriftReport;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift;
import us.pochaev.dfs.scanner.infra.port.FileAttributesReader;

import java.util.Objects;

/**
 * Checks a previewed plan for drift since its scan (DFS-033) through a {@link FileAttributesReader}: validates the
 * whole plan against the scan before any read, then reads each distinct path once in plan order and compares it with
 * the recorded {@code FileEntry}. Its only field is the port, so concurrent calls share nothing.
 */
public final class CheckPlanDriftService implements CheckPlanDrift {

    private final FileAttributesReader reader;

    public CheckPlanDriftService(FileAttributesReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public Result<DriftReport> check(ScanResult scan, DeletionPlan plan) {
        throw new UnsupportedOperationException("not implemented: DFS-033");
    }
}
