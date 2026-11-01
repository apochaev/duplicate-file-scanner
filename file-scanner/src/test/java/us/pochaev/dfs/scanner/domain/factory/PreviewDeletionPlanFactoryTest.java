package us.pochaev.dfs.scanner.domain.factory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.Operation;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.PreviewDeletionPlan;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PreviewDeletionPlanFactoryTest {

    @Test
    @DisplayName("DFS-022.AC-14 ScannerFactories.previewDeletionPlan() returns a working preview")
    void factoryReturnsWorkingPreview() {
        PreviewDeletionPlan preview = ScannerFactories.previewDeletionPlan();
        assertNotNull(preview);
        ScanResult scan = new ScanResult(List.of(), List.of(), List.of(new DuplicateSet(10, "d1", List.of(
                new FileEntry(Path.of("C:/A/x.jpg"), 10, Instant.EPOCH),
                new FileEntry(Path.of("C:/B/x.jpg"), 10, Instant.EPOCH)))), List.of(), 0);
        PlanRequest request = new PlanRequest(
                List.of(new KeepAndQuarantine(Path.of("C:/A"), Path.of("C:/B"))), List.of(), List.of());
        Result<DeletionPlan> result = preview.preview(scan, request);
        Result.Success<?> success = assertInstanceOf(Result.Success.class, result);
        DeletionPlan plan = (DeletionPlan) success.value();
        assertEquals(1, plan.operations().size());
        assertEquals(List.of(Path.of("C:/B/x.jpg")), ((Operation.Quarantine) plan.operations().get(0)).links());
    }
}
